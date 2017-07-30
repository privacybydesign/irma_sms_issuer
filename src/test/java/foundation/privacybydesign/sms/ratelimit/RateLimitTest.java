package foundation.privacybydesign.sms.ratelimit;

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test whether the canonicalization algorithm for IP addresses works well.
 */
public class RateLimitTest {
    @Test
    public void testIPv4Address() {
        assertEquals("1.2.3.4",
                RateLimit.getAddressPrefix("1.2.3.4"));
    }

    @Test
    public void testIPv6AddressLocal() {
        assertEquals("0:0:0:0:0:0:0:0",
                RateLimit.getAddressPrefix("0:0:0:0:0:0:0:1"));
    }

    @Test
    public void testIPv6AddressRegular() {
        assertEquals("2a00:123:4567:8900:0:0:0:0",
                RateLimit.getAddressPrefix("2a00:0123:4567:89ab:cdef:fedc:ba98:7654"));
    }

    @Test
    public void testIPv6Equals() {
        String addr1 = RateLimit.getAddressPrefix("2a00:0123:4567:89ab:cdef:fedc:ba98:7654");
        String addr2 = RateLimit.getAddressPrefix("2a00:0123:4567:89ac:cdef:fedc:ba98:7654");
        assertTrue("IPv6 addresses should match", addr1.equals(addr2));
    }

    @Test
    public void testIPv6Unequals() {
        String addr1 = RateLimit.getAddressPrefix("2a00:0123:4567:89ab:cdef:fedc:ba98:7654");
        String addr2 = RateLimit.getAddressPrefix("2a00:0123:4567:88ab:cdef:fedc:ba98:7654");
        assertFalse("IPv6 addresses should not match", addr1.equals(addr2));
    }
}
