package foundation.privacybydesign.sms.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Base class for rate limiting. Subclasses provide storage methods (memory
 * for easier debugging and database for production).
 */
public abstract class RateLimit {
    private static Logger logger = LoggerFactory.getLogger(RateLimit.class);

    // pattern made package-private for testing
    static final String PHONE_PATTERN = "(0|\\+31|0031)6[1-9][0-9]{7}";

    /** Take an IP address and a phone number and rate limit them.
     * @param remoteAddr IP address (IPv4 or IPv6 in any format)
     * @param phone phone number
     * @return the number of milliseconds that the client should wait - 0 if
     *         it shouldn't wait.
     */
    public long rateLimited(String remoteAddr, String phone)
            throws InvalidPhoneNumberException {
        String addr = getAddressPrefix(remoteAddr);
        phone = canonicalPhoneNumber(phone);
        long now = System.currentTimeMillis();
        long ipRetryAfter = nextTryIP(addr, now);
        long phoneRetryAfter = nextTryPhone(phone, now);
        long retryAfter = Math.max(ipRetryAfter, phoneRetryAfter);
        if (retryAfter > now) {
            logger.warn("Denying request from {}: rate limit (ip and/or phone) exceeded", addr);
            // Don't count this request if it has been denied.
            return retryAfter - now;
        }
        countIP(addr, now);
        countPhone(phone, now);
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

    public static String canonicalPhoneNumber(String phone)
            throws InvalidPhoneNumberException {
        if (!phone.matches(PHONE_PATTERN)) {
            throw new InvalidPhoneNumberException();
        }
        if (phone.startsWith("06")) {
            return "+31" + phone.substring(1);
        }
        if (phone.startsWith("00316")) {
            return "+31" + phone.substring(4);
        }
        return phone;
    }

    protected abstract long nextTryIP(String ip, long now);
    protected abstract long nextTryPhone(String phone, long now);
    protected abstract long countIP(String ip, long now);
    protected abstract long countPhone(String phone, long now);
}
