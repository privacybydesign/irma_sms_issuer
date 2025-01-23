package foundation.privacybydesign.sms.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import foundation.privacybydesign.sms.common.Hmac;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Base class for rate limiting. Subclasses provide storage methods (memory
 * for easier debugging and database for production).
 */
public abstract class RateLimit {
    private static Logger logger = LoggerFactory.getLogger(RateLimit.class);

    /**
     * Take an IP address and a phone number and rate limit them.
     * 
     * @param remoteAddr IP address (IPv4 or IPv6 in any format)
     * @param phone      phone number
     * @return the number of milliseconds that the client should wait - 0 if
     *         it shouldn't wait.
     * @throws NoSuchAlgorithmException If the hashing algorithm in use isn't available on the system, this exception is thrown.
     */
    public long rateLimited(String remoteAddr, String phone, Hmac hmac)
            throws InvalidPhoneNumberException, NoSuchAlgorithmException, InvalidKeyException {
        long now = System.currentTimeMillis();
        
        String addr = getAddressPrefix(remoteAddr);

        final String ipHash = hmac.createHmac(addr);
        final String phoneHash = hmac.createHmac(phone);

        long ipRetryAfter = nextTryIP(ipHash, now);
        long phoneRetryAfter = nextTryPhone(phoneHash, now);
        long retryAfter = Math.max(ipRetryAfter, phoneRetryAfter);
        if (retryAfter > now) {
            // Explicitly log the IP address, as it might be needed for investigations
            logger.warn("Denying request from {}: rate limit (ip and/or phone) exceeded", addr);
            // Don't count this request if it has been denied.
            return retryAfter - now;
        }
        countIP(ipHash, now);
        countPhone(phoneHash, now);
        return 0;
    }

    /**
     * Insert an IP address (IPv4 or IPv6) and get a canonicalized version.
     * For IPv6, also truncate to /56 (recommended residential block).
     *
     * This is a public method to ease testing.
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
            for (int i = 7; i < 16; i++) {
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

    public abstract void periodicCleanup();

    protected abstract long nextTryIP(String ipHash, long now);

    protected abstract long nextTryPhone(String phoneHash, long now);

    protected abstract void countIP(String ipHash, long now);

    protected abstract void countPhone(String phoneHash, long now);
}
