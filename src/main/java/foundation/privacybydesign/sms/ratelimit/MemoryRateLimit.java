package foundation.privacybydesign.sms.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

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
    private static final long WEEK = DAY * 7;
    private static final int TIMEOUT = 10; // timeout in seconds
    private static final int TRIES = 3;    // number of tries on first visit

    private static MemoryRateLimit instance;

    private HashMap<String, Long> ipLimits;
    private HashMap<String, Limit> phoneLimits;

    public MemoryRateLimit() {
        ipLimits = new HashMap<>();
        phoneLimits = new HashMap<>();
    }

    public static MemoryRateLimit getInstance() {
        if (instance == null) {
            instance = new MemoryRateLimit();
        }
        return instance;
    }

    protected synchronized long nextTryIP(String ip, long now) {
        // Allow at most 1 try in each period (TIMEOUT), but kick in only
        // after 3 tries. Thus while the user can do only 1 try per period
        // over longer periods, the initial budget is 3 periods.
        final long period = TIMEOUT*1000;
        long limit = 0; // First try - last try was "long in the past".
        if (ipLimits.containsKey(ip)) {
            // Ah, there was a request before.
            limit = ipLimits.get(ip);
        }

        long startLimit = now - period*TRIES;
        if (limit < startLimit) {
            // First visit or previous visit was long ago.
            // Act like the last try was 3 periods ago.
            limit = startLimit;
        }

        // Add a period to the current limit.
        limit += period;
        return limit;
    }

    protected synchronized void countIP(String ip, long now) {
        long nextTry = nextTryIP(ip, now);
        if (nextTry > now) {
            throw new IllegalStateException("counting rate limit while over the limit");
        }
        ipLimits.put(ip, nextTry);
    }

    // Is the user over the rate limit per phone number?
    protected synchronized long nextTryPhone(String phone, long now) {
        // Rate limiter durations (sort-of logarithmic):
        // 1   10 second
        // 2   5 minute
        // 3   12 hour
        // 4   1 week
        // 5+  1 per week
        // Keep log 4 weeks for proper limiting.

        Limit limit = phoneLimits.get(phone);
        if (limit == null) {
            limit = new Limit();
            phoneLimits.put(phone, limit);
        }
        long nextTry; // timestamp when the next request is allowed
        switch (limit.tries) {
            case 0: // try 1: always succeeds
                nextTry = now;
                break;
            case 1: // try 2: allowed after 10 seconds
                nextTry = limit.timestamp + 10 * SECOND;
                break;
            case 2: // try 3: allowed after 5 minutes
                nextTry = limit.timestamp + 5 * MINUTE;
                break;
            case 3: // try 4: allowed after 12 hours
                nextTry = limit.timestamp + 12 * HOUR;
                break;
            case 4: // try 5: allowed after 1 week
                nextTry = limit.timestamp + 1 * WEEK;
                break;
            default:
                throw new IllegalStateException("invalid tries count");
        }
        return nextTry;
    }

    // Count the usage of this rate limit - adding to the budget for this
    // phone number.
    protected synchronized void countPhone(String phone, long now) {
        long nextTry = nextTryPhone(phone, now);
        Limit limit = phoneLimits.get(phone);
        if (nextTry > now) {
            throw new IllegalStateException("counting rate limit while over the limit");
        }
        limit.tries = Math.min(limit.tries+1, 5); // add 1, max at 5
        // If the last usage was e.g. ≥3 weeks ago, we should allow them 3
        // extra tries this week. But don't go below 1 limit in the counter.
        limit.tries = (int)Math.max(1, limit.tries - (now-limit.timestamp)/WEEK);
        limit.timestamp = now;
    }
}

class Limit {
    public long timestamp;
    public int tries;

    Limit() {
        tries = 0;
        timestamp = 0;
    }
}
