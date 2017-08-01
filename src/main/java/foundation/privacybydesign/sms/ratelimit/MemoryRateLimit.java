package foundation.privacybydesign.sms.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
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

    public MemoryRateLimit() {
        ipLimits = new HashMap<>();
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
            // Add a period to the
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
}
