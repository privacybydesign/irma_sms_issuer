package foundation.privacybydesign.sms.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Base class for rate limiting. Subclasses provide storage methods (memory
 * for easier debugging and database for production).
 */
public abstract class RateLimit {
    /** Take an IP address and a phone number and rate limit them.
     * @param remoteAddr IP address (IPv4 or IPv6 in any format)
     * @param phone phone number
     * @return the number of milliseconds that the client should wait - 0 if
     *         it shouldn't wait.
     */
    public long rateLimited(String remoteAddr, String phone) {
        String addr = getAddressPrefix(remoteAddr);
        // Note: when the phone limit is reached, the IP limit is incremented
        // which might not be desired.
        long retryAfter = rateLimitedIP(addr);
        if (retryAfter > 0) {
            // TODO: also take into account the phone rate limit
            return retryAfter;
        }
        retryAfter = rateLimitedPhone(addr, phone);
        if (retryAfter > 0) {
            return retryAfter;
        }
        return 0;
    }

    /** Insert an IP address (IPv4 or IPv6) and get a canonicalized version.
     *  For IPv6, also truncate to /56 (recommended residential block).
     *
     *  This is a public method to ease testing.
     */
    public static String getAddressPrefix(String remoteAddr) {
        byte[] rawAddr;
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr);
            rawAddr = addr.getAddress();
        } catch (UnknownHostException e) {
            // Shouldn't be possible - we're using IP addresses here, not
            // host names.
            throw new RuntimeException("host name lookup on IP address?");
        }
        if (rawAddr.length == 4) { // IPv4
            // take the whole IP address
        } else if (rawAddr.length == 16) { // IPv6
            // Use only the first /56 bytes, set the rest to 0.
            for (int i=7; i<16; i++) {
                rawAddr[i] = 0;
            }
        } else {
            // I hope this will never happen.
            throw new RuntimeException("no IPv4 or IPv6?");
        }
        try {
            return InetAddress.getByAddress(rawAddr).getHostAddress();
        } catch (UnknownHostException e) {
            // Should Not Happen™
            throw new RuntimeException("host name lookup on IP address?");
        }
    }

    protected abstract long rateLimitedIP(String ip);
    protected abstract long rateLimitedPhone(String ip, String phone);
}
