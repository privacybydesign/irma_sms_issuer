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
        System.out.println("check " + ip);
        if (!ipLimits.containsKey(ip)) {
            System.out.println("first visit");
            // First visit
            ipLimits.put(ip, System.currentTimeMillis());
        } else {
            System.out.println("visited before");
            // Visited before
            long now = System.currentTimeMillis();
            if (ipLimits.get(ip) + TIMEOUT*1000 >= now) {
                // Rate limited!
                logger.warn("Denying request from {}!", ip);
                ipLimits.put(ip, now);
                return true;
            }
        }
        return false;
    }
}
