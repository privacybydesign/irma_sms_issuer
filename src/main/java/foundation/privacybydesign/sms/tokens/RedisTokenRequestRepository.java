package foundation.privacybydesign.sms.tokens;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import foundation.privacybydesign.sms.redis.Redis;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
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
    private static final String createdFieldName = "created";

    JedisSentinelPool pool;

    RedisTokenRequestRepository() {
        pool = Redis.createSentinelPoolFromEnv();
    }

    @Override
    public void store(String phoneHash, TokenRequest request) {
        final String key = Redis.createKey(namespace, phoneHash);
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
    public void remove(String phoneHash) {
        final String key = Redis.createKey(namespace, phoneHash);

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
                LOG.error("Failed to parse creation into long.", e);
            }
        }
    }

    @Override
    public TokenRequest retrieve(String phoneHash) {
        final String key = Redis.createKey(namespace, phoneHash);
        try (var jedis = pool.getResource()) {
            try {
                final Map<String, String> fields = jedis.hgetAll(key);

                final String token = fields.get(tokenFieldName);
                final int tries = Integer.parseInt(fields.get(triesFieldName));
                final long created = Long.parseLong(fields.get(createdFieldName));

                return new TokenRequest(token, tries, created);
            } catch (NumberFormatException e) {
                LOG.error("Failed to parse tries or created field", e);
                return null;
            }
        }
    }
}
