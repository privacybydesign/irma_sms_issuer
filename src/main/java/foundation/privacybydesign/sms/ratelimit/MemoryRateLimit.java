package foundation.privacybydesign.sms.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store rate limits in memory. Useful for debugging and rate limits that
 * aren't very long.
 *
 * How it works:
 * How much budget a user has, is expressed in a timestamp. The timestamp is
 * initially some period in the past, but with every usage (countIP and
 * countPhone) this timestamp is incremented. For IP address counting, this
 * is a fixed amount (currently 10 seconds), but for phone numbers this
 * amount is exponential.
 *
 * An algorithm with a similar goal is the Token Bucket algorithm. This
 * algorithm probably works well, but seemed harder to implement.
 * https://en.wikipedia.org/wiki/Token_bucket
 */
public class MemoryRateLimit extends RateLimit {
    private static final long SECOND = 1000; // 1000ms = 1s
    private static final long MINUTE = SECOND * 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;
    private static final int IP_TIMEOUT = 10 * 1000; // timeout in seconds
    private static final int IP_TRIES = 3; // number of tries on first visit

    private static MemoryRateLimit instance;

    private final Map<String, Long> ipLimits;
    private final Map<String, Limit> phoneLimits;

    public MemoryRateLimit() {
        ipLimits = new ConcurrentHashMap<>();
        phoneLimits = new ConcurrentHashMap<>();
    }

    public static MemoryRateLimit getInstance() {
        if (instance == null) {
            instance = new MemoryRateLimit();
        }
        return instance;
    }

    private long startLimitIP(long now) {
        return now - IP_TIMEOUT * IP_TRIES;
    }

    @Override
    protected synchronized long nextTryIP(String ip, long now) {
        // Allow at most 1 try in each period (TIMEOUT), but kick in only
        // after 3 tries. Thus while the user can do only 1 try per period
        // over longer periods, the initial budget is 3 periods.
        long limit = 0; // First try - last try was "long in the past".
        if (ipLimits.containsKey(ip)) {
            // Ah, there was a request before.
            limit = ipLimits.get(ip);
        }

        long startLimit = startLimitIP(now);
        if (limit < startLimit) {
            // First visit or previous visit was long ago.
            // Act like the last try was 3 periods ago.
            limit = startLimit;
        }

        // Add a period to the current limit.
        limit += IP_TIMEOUT;
        return limit;
    }

    @Override
    protected synchronized void countIP(String ip, long now) {
        long nextTry = nextTryIP(ip, now);
        if (nextTry > now) {
            throw new IllegalStateException("counting rate limit while over the limit");
        }
        ipLimits.put(ip, nextTry);
    }

    // Is the user over the rate limit per phone number?
    @Override
    protected synchronized long nextTryPhone(String phone, long now) {
        // Rate limiter durations (sort-of logarithmic):
        // 1 10 second
        // 2 5 minute
        // 3 1 hour
        // 4 24 hour
        // 5+ 1 per day
        // Keep log 5 days for proper limiting.

        Limit limit = phoneLimits.get(phone);
        if (limit == null) {
            limit = new Limit(now);
            phoneLimits.put(phone, limit);
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

    // Count the usage of this rate limit - adding to the budget for this
    // phone number.
    @Override
    protected synchronized void countPhone(String phone, long now) {
        long nextTry = nextTryPhone(phone, now);
        Limit limit = phoneLimits.get(phone);
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
    }

    public void periodicCleanup() {
        long now = System.currentTimeMillis();
        // Use enhanced for loop, because an iterator makes sure concurrency issues
        // cannot occur.
        for (Map.Entry<String, Long> entry : ipLimits.entrySet()) {
            if (entry.getValue() < startLimitIP(now)) {
                ipLimits.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, Limit> entry : phoneLimits.entrySet()) {
            if (entry.getValue().timestamp < now - 5 * DAY) {
                phoneLimits.remove(entry.getKey());
            }
        }
    }
}

class Limit {
    long timestamp;
    int tries;

    Limit(long now) {
        tries = 0;
        timestamp = now;
    }

    Limit(long timestamp, int tries) {
        this.timestamp = timestamp;
        this.tries = tries;
    }
}
