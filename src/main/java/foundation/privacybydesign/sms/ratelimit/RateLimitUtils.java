
package foundation.privacybydesign.sms.ratelimit;

public class RateLimitUtils {
    /// Returns the active rate limiter based on the configuration
    public static RateLimit getRateLimiter() {
        final String storageType = System.getenv("STORAGE_TYPE");
        if (storageType.equals("redis")) {
            return RedisRateLimit.getInstance();
        }
        return MemoryRateLimit.getInstance();
    }
}
