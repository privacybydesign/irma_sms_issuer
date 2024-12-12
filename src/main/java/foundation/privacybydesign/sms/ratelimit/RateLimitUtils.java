
package foundation.privacybydesign.sms.ratelimit;

public class RateLimitUtils {
    /// Returns the active rate limiter based on the configuration
    public static RateLimit getRateLimiter() {
        final String type = System.getenv("STORAGE_TYPE");
        if (type == "redis") {
            return RedisRateLimit.getInstance();
        }
        return MemoryRateLimit.getInstance();
    }
}
