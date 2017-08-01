package foundation.privacybydesign.sms.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Store rate limits in memory. Useful for debugging.
 */
public class MemoryRateLimit extends RateLimit {
    private static final int TIMEOUT = 10; // timeout in seconds
    private static final int TRIES = 3;    // number of tries on first visit

    private static MemoryRateLimit instance;
    private static Logger logger = LoggerFactory.getLogger(MemoryRateLimit.class);

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

    protected synchronized boolean rateLimitedIP(String ip) {
        // Allow at most 1 try in each period (TIMEOUT), but kick in only
        // after 3 tries.
        long now = System.currentTimeMillis();
        long startLimit = now - TIMEOUT*1000*(TRIES-1);
        if (!ipLimits.containsKey(ip)) {
            // First visit
            // Act like the last try was 3 periods ago.
            ipLimits.put(ip, startLimit);
        } else {
            // Visited before
            long limit = ipLimits.get(ip);
            // Add a period to the current limit
            limit = Math.max(startLimit, limit + TIMEOUT*1000);
            // But if I try 100 times in one period (of which 97 are denied)
            // I don't want to wait 97 periods - just one. I haven't actually
            // used (much) resources those 97 periods or have removed a rogue
            // user from my network etc.
            if (limit > now) {
                limit = now;
            }
            ipLimits.put(ip, limit);
            if (limit >= now) {
                // Rate limited!
                logger.warn("Denying request from {}!", ip);
                return true;
            }
        }
        return false;
    }

    protected synchronized boolean rateLimitedPhone(String phone) {
        // Rate limiter durations (sort-of logarithmic):
        // 1   10 second
        // 2   5 minute
        // 3   12 hour
        // 4   1 week
        // 5+  1 per week
        // Keep log 4 weeks for proper limiting.

        final long SECOND = 1000; // 1000ms = 1s
        final long MINUTE = SECOND * 60;
        final long HOUR = MINUTE * 60;
        final long DAY = HOUR * 24;
        final long WEEK = DAY * 7;

        long now = System.currentTimeMillis();

        Limit limit = phoneLimits.get(phone);
        if (limit == null) {
            limit = new Limit();
            phoneLimits.put(phone, limit);
        }
        System.out.println("current phone limit: " + limit.tries);
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
                throw new RuntimeException("invalid tries count");
        }
        if (nextTry > now) {
            // Denying this request.
            // Don't count this request, as the user hasn't actually caused
            // any load on the system (yet).
            System.out.println("Denied. Try again in: " + (nextTry-now)/1000);
            return true;
        }
        // Allowing this request, but counting the usage.
        limit.tries = Math.min(limit.tries+1, 5); // add 1, max at 5
        // If the last usage was e.g. ≥3 weeks ago, we should allow them 3
        // extra tries this week. But don't go below 1 limit in the counter.
        limit.tries = (int)Math.max(1, limit.tries - (now-limit.timestamp)/WEEK);
        limit.timestamp = now;
        System.out.println("Allowed. Now at tries: " + limit.tries);
        return false;
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
