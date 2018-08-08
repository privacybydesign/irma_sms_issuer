package foundation.privacybydesign.sms.ratelimit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Test various valid and invalid phone numbers and test the
 * phone number canonicalization algorithm.
 */
public class PhoneRegexTest {
    @Test(expected=InvalidPhoneNumberException.class)
    public void testNonInternational() throws InvalidPhoneNumberException {
        RateLimit.canonicalPhoneNumber("0612345678");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testNotMobile() throws InvalidPhoneNumberException {
        RateLimit.canonicalPhoneNumber("+31712345678");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testEmpty() throws InvalidPhoneNumberException {
        RateLimit.canonicalPhoneNumber("");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testTooShort() throws InvalidPhoneNumberException {
        RateLimit.canonicalPhoneNumber("+31612345");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testTooLong() throws InvalidPhoneNumberException {
        RateLimit.canonicalPhoneNumber("+316123456789");
    }

    @Test()
    public void testDutch() throws InvalidPhoneNumberException {
        assertEquals("+31612345678", RateLimit.canonicalPhoneNumber("+31612345678"));
    }

    @Test()
    public void testGerman() throws InvalidPhoneNumberException {
        assertEquals("+4915112345678", RateLimit.canonicalPhoneNumber("+4915112345678"));
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testGermanNotMobile() throws InvalidPhoneNumberException {
        assertEquals("+493012345678", RateLimit.canonicalPhoneNumber("+493012345678"));
    }

    @Test()
    public void testSpain() throws InvalidPhoneNumberException {
        assertEquals("+34712345678", RateLimit.canonicalPhoneNumber("+34712345678"));
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testSpainNotMobile() throws InvalidPhoneNumberException {
        assertEquals("+34112345678", RateLimit.canonicalPhoneNumber("+34112345678"));
    }

    @Test()
    public void testFrance() throws InvalidPhoneNumberException {
        assertEquals("+33612345678", RateLimit.canonicalPhoneNumber("+33612345678"));
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testUS() throws InvalidPhoneNumberException {
        RateLimit.canonicalPhoneNumber("+12015550123");
    }
}
