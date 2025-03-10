package foundation.privacybydesign.sms.ratelimit;

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

public class RedisRateLimit extends RateLimit {
    private static final long SECOND = 1000; // 1000ms = 1s
    private static final long MINUTE = SECOND * 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;
    private static final int IP_TIMEOUT = 10 * 1000; // timeout in seconds
    private static final int IP_TRIES = 3; // number of tries on first visit

    private static Logger LOG = LoggerFactory.getLogger(RedisRateLimit.class);
    private static RedisRateLimit instance;

    private static final String ipLimitsNamespace = "ip-limits";
    private static final String phoneLimitsNamespace = "phone-limits:";
    private static final String timestampFieldName = "timestamp";
    private static final String triesFieldName = "tries";

    public static RateLimit getInstance() {
        if (instance == null) {
            instance = new RedisRateLimit();
        }
        return instance;
    }

    JedisSentinelPool pool;

    RedisRateLimit() {
        pool = Redis.createSentinelPoolFromEnv();
    }

    @Override
    protected long nextTryIP(String ipHash, long now) {
        final String key = Redis.createKey(ipLimitsNamespace, ipHash);

        // Allow at most 1 try in each period (TIMEOUT), but kick in only
        // after 3 tries. Thus while the user can do only 1 try per period
        // over longer periods, the initial budget is 3 periods.
        long limit = 0;
        try (var jedis = pool.getResource()) {
            // Ah, there was a request before.
            if (jedis.exists(key)) {
                try {
                    limit = Long.parseLong(jedis.get(key));
                } catch (NumberFormatException e) {
                    LOG.error("failed to parse long from " + key + ": " + e.getMessage());
                }
            }
        }

        final long startLimit = startLimitIP(now);
        if (limit < startLimit) {
            // First visit or previous visit was long ago.
            // Act like the last try was 3 periods ago.
            limit = startLimit;
        }

        // Add a period to the current limit.
        limit += IP_TIMEOUT;
        return limit;
    }

    private long startLimitIP(long now) {
        return now - IP_TIMEOUT * IP_TRIES;
    }

    @Override
    protected synchronized long nextTryPhone(String phoneHash, long now) {
        // Rate limiter durations (sort-of logarithmic):
        // 1 10 second
        // 2 5 minute
        // 3 1 hour
        // 4 24 hour
        // 5+ 1 per day
        // Keep log 5 days for proper limiting.

        final String key = Redis.createKey(phoneLimitsNamespace, phoneHash);

        Limit limit;

        try (var jedis = pool.getResource()) {
            limit = limitFromRedis(jedis, key);
            if (limit == null) {
                limit = new Limit(now);
                limitToRedis(jedis, key, limit);
            }
        }

        long nextTry; // timestamp when the next request is allowed
        switch (limit.tries) {
            case 0: // try 1: always succeeds
                nextTry = limit.timestamp;
                break;
            case 1: // try 2: allowed after 10 seconds
                nextTry = limit.timestamp + 10 * SECOND;
                break;
            case 2: // try 3: allowed after 5 minutes
                nextTry = limit.timestamp + 5 * MINUTE;
                break;
            case 3: // try 4: allowed after 3 hours
                nextTry = limit.timestamp + 3 * HOUR;
                break;
            case 4: // try 5: allowed after 24 hours
                nextTry = limit.timestamp + 24 * HOUR;
                break;
            default:
                throw new IllegalStateException("invalid tries count");
        }
        return nextTry;
    }

    @Override
    protected synchronized void countIP(String ipHash, long now) {
        final long nextTry = nextTryIP(ipHash, now);
        if (nextTry > now) {
            throw new IllegalStateException("counting rate limit while over the limit");
        }

        final String key = Redis.createKey(ipLimitsNamespace, ipHash);
        try (var jedis = pool.getResource()) {
            jedis.set(key, Long.toString(nextTry));
        }
    }

    // Count the usage of this rate limit - adding to the budget for this
    // phone number.
    @Override
    protected synchronized void countPhone(String phoneHash, long now) {
        final long nextTry = nextTryPhone(phoneHash, now);

        try (var jedis = pool.getResource()) {
            final String key = Redis.createKey(phoneLimitsNamespace, phoneHash);
            Limit limit = limitFromRedis(jedis, key);

            if (limit == null) {
                throw new IllegalStateException("limit not found in redis");
            }

            if (nextTry > now) {
                throw new IllegalStateException("counting rate limit while over the limit");
            }

            limit.tries = Math.min(limit.tries + 1, 5); // add 1, max at 5
            // If the last usage was e.g. ≥2 days ago, we should allow them 2 tries
            // extra tries this day.
            long lastTryDaysAgo = (now - limit.timestamp) / DAY;
            long bonusTries = limit.tries - lastTryDaysAgo;
            if (bonusTries >= 1) {
                limit.tries = (int) bonusTries;
            }
            limit.timestamp = now;

            limitToRedis(jedis, key, limit);
        }
    }

    @Override
    public void periodicCleanup() {
        cleanUpIpLimits();
        cleanUpPhoneLimits();
    }

    /**
     * @param jedis
     * @param key
     * @return null if limit was not found
     */
    static Limit limitFromRedis(Jedis jedis, String key) {
        // doing a redis transaction because there's multiple values to be set
        // and setting them atomically ensures no race condition can occur
        jedis.watch(key);
        Transaction transaction = jedis.multi();

        final Response<String> tsResult = transaction.hget(key, timestampFieldName);
        final Response<String> triesResponse = transaction.hget(key, triesFieldName);

        final List<Object> transactionResult = transaction.exec();

        if (transactionResult == null) {
            LOG.error("failed to get limit from redis: exec result == null");
            return null;
        }

        for (var r : transactionResult) {
            if (r instanceof Exception) {
                LOG.error("an error occurred while getting limit from redis: " + ((Exception) r).getMessage());
                return null;
            }
        }

        final String ts = tsResult.get();
        final String tries = triesResponse.get();

        try {
            return new Limit(Long.parseLong(ts), Integer.parseInt(tries));
        } catch (NumberFormatException e) {
            LOG.error("failed to parse string to int: " + e.getMessage());
            return null;
        }
    }

    static void limitToRedis(Jedis jedis, String key, Limit limit) {
        jedis.watch(key);
        Transaction transaction = jedis.multi();

        transaction.hset(key, timestampFieldName, Long.toString(limit.timestamp));
        transaction.hset(key, triesFieldName, Integer.toString(limit.tries));

        final List<Object> results = transaction.exec();
        if (results == null) {
            LOG.error("failed to put limit to redis: exec result == null");
            return;
        }

        for (var r : results) {
            if (r instanceof Exception) {
                LOG.error("an error occurred while setting limit to redis: " + ((Exception) r).getMessage());
            }
        }
    }

    // TODO: This is not the idiomatic way to delete expired items in Redis,
    // use the built in `expire` command instead
    private void cleanUpIpLimits() {
        final long now = System.currentTimeMillis();

        final String pattern = Redis.createNamespace(ipLimitsNamespace) + "*";
        ScanParams scanParams = new ScanParams().match(pattern);
        String cursor = "0";

        try (var jedis = pool.getResource()) {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();
                cursor = scanResult.getCursor();

                for (String key : keys) {
                    String word = jedis.get(key);
                    try {
                        long value = Long.parseLong(word);
                        if (value < startLimitIP(now)) {
                            jedis.del(key);
                        }
                    } catch (NumberFormatException e) {
                        LOG.error("failed to parse: " + e.getMessage());
                    }
                }
            } while (!cursor.equals("0")); // continue until the cursor wraps around
        }
    }

    // TODO: This is not the idiomatic way to delete expired items in Redis,
    // use the built in `expire` command instead
    private void cleanUpPhoneLimits() {
        final long now = System.currentTimeMillis();

        final String pattern = Redis.createNamespace(phoneLimitsNamespace) + "*";
        ScanParams scanParams = new ScanParams().match(pattern);
        String cursor = "0";

        try (var jedis = pool.getResource()) {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();
                cursor = scanResult.getCursor();

                for (String key : keys) {
                    Limit limit = limitFromRedis(jedis, key);
                    if (limit != null && limit.timestamp < now - 5 * DAY) {
                        jedis.del(key);
                    }
                }
            } while (!cursor.equals("0")); // continue until the cursor wraps around
        }
    }
}
