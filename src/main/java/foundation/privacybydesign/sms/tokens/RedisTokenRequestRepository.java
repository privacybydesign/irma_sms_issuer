package foundation.privacybydesign.sms.tokens;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import foundation.privacybydesign.sms.redis.Redis;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * A token repository that stores and retrieves tokens from a redis store.
 * Useful for when the sms issuer needs to be stateless.
 */
class RedisTokenRequestRepository implements TokenRequestRepository {
    private static Logger logger = LoggerFactory.getLogger(RedisTokenRequestRepository.class);
    private static String namespace = "request";
    JedisSentinelPool pool;

    RedisTokenRequestRepository() {
        pool = Redis.createSentinelPoolFromEnv();
    }

    @Override
    public void store(String phone, TokenRequest request) {
        final String key = Redis.createKey(namespace, phone);
        try (var jedis = pool.getResource()) {
            jedis.hset(key, "token", request.token);
            jedis.hset(key, "tries", Integer.toString(request.tries));
            jedis.hset(key, "created", String.valueOf(request.created));
        }
    }

    @Override
    public void remove(String phone) {
        final String key = Redis.createKey(namespace, phone);

        try (var jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

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
        String createdStr = jedis.hget(key, "created");
        if (createdStr != null) {
            try {
                long created = Long.parseLong(createdStr);
                if (TokenRequest.isExpiredForCreationDate(created)) {
                    jedis.del(key);
                }
            } catch (NumberFormatException e) {
                logger.error("Failed to parse " + key + " creation into long: " + e.getMessage());
            }
        }
    }

    @Override
    public TokenRequest retrieve(String phone) {
        try (var jedis = pool.getResource()) {
            final String key = Redis.createKey(namespace, phone);
            try {
                String token = jedis.hget(key, "token");
                int tries = Integer.parseInt(jedis.hget(key, "tries"));
                long created = Long.parseLong(jedis.hget(key, "created"));
                return new TokenRequest(token, tries, created);
            } catch (NumberFormatException e) {
                logger.error("failed to parse for " + key + ": " + e.getMessage());
                return null;
            }
        }
    }
}
