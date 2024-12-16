package foundation.privacybydesign.sms.tokens;

import java.awt.Panel;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import foundation.privacybydesign.sms.redis.Redis;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * A token repository that stores and retrieves tokens from a redis store.
 * Useful for when the sms issuer needs to be stateless.
 */
class RedisTokenRequestRepository implements TokenRequestRepository {
    private static Logger LOG = LoggerFactory.getLogger(RedisTokenRequestRepository.class);
    private static final String namespace = "request";
    private static final String tokenFieldName = "token";
    private static final String triesFieldName = "tries";
    private static final String createdFieldName = "tries";

    JedisSentinelPool pool;

    RedisTokenRequestRepository() {
        pool = Redis.createSentinelPoolFromEnv();
    }

    @Override
    public void store(String phone, TokenRequest request) {
        final String key = Redis.createKey(namespace, phone);
        try (var jedis = pool.getResource()) {
            jedis.watch(key);

            Transaction transaction = jedis.multi();
            transaction.hset(key, tokenFieldName, request.token);
            transaction.hset(key, triesFieldName, Integer.toString(request.tries));
            transaction.hset(key, createdFieldName, String.valueOf(request.created));

            final List<Object> result = transaction.exec();

            if (result == null) {
                LOG.error("failed to set token: redis transaction exec() result is null");
                return;
            }
            for (var r : result) {
                if (r instanceof Exception) {
                    LOG.error("failed to set token: " + ((Exception) r).getMessage());
                }
            }
        }
    }

    @Override
    public void remove(String phone) {
        final String key = Redis.createKey(namespace, phone);

        try (var jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    // TODO: This is not the idiomatic way to delete expired items in Redis,
    // use the built in `expire` command instead
    @Override
    public void removeExpired() {
        final String pattern = Redis.createNamespace(namespace) + "*";
        ScanParams scanParams = new ScanParams().match(pattern);
        String cursor = "0";

        try (var jedis = pool.getResource()) {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();
                cursor = scanResult.getCursor();

                for (String key : keys) {
                    removeIfExpired(jedis, key);
                }
            } while (!cursor.equals("0")); // continue until the cursor wraps around
        }
    }

    private void removeIfExpired(Jedis jedis, String key) {
        final String createdStr = jedis.hget(key, createdFieldName);
        if (createdStr != null) {
            try {
                final long created = Long.parseLong(createdStr);
                if (TokenRequest.isExpiredForCreationDate(created)) {
                    jedis.del(key);
                }
            } catch (NumberFormatException e) {
                LOG.error("Failed to parse " + key + " creation into long: " + e.getMessage());
            }
        }
    }

    @Override
    public TokenRequest retrieve(String phone) {
        try (var jedis = pool.getResource()) {
            final String key = Redis.createKey(namespace, phone);
            try {

                jedis.watch(key);
                Transaction transaction = jedis.multi();

                final Response<String> tokenRes = transaction.hget(key, tokenFieldName);
                final Response<String> triesRes = transaction.hget(key, triesFieldName);
                final Response<String> createdRes = transaction.hget(key, createdFieldName);

                final List<Object> execRes = transaction.exec();

                if (execRes == null) {
                    LOG.error("error while getting token request from redis: exec result is null");
                    return null;
                }

                for (var r : execRes) {
                    if (r instanceof Exception) {
                        LOG.error("error while getting token request from redis: " + ((Exception) r).getMessage());
                        return null;
                    }
                }

                final String token = tokenRes.get();
                final int tries = Integer.parseInt(triesRes.get());
                final long created = Long.parseLong(createdRes.get());

                return new TokenRequest(token, tries, created);
            } catch (NumberFormatException e) {
                LOG.error("failed to parse for " + key + ": " + e.getMessage());
                return null;
            }
        }
    }
}
