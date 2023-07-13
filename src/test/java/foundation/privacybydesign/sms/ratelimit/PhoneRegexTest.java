package foundation.privacybydesign.sms.ratelimit;

import foundation.privacybydesign.sms.SMSRestApi;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test various valid and invalid phone numbers and test the
 * phone number canonicalization algorithm.
 */
public class PhoneRegexTest {
    @Test(expected=InvalidPhoneNumberException.class)
    public void testNonInternational() throws InvalidPhoneNumberException {
        SMSRestApi.canonicalPhoneNumber("0612345678");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testNotMobile() throws InvalidPhoneNumberException {
        SMSRestApi.canonicalPhoneNumber("+31712345678");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testEmpty() throws InvalidPhoneNumberException {
        SMSRestApi.canonicalPhoneNumber("");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testTooShort() throws InvalidPhoneNumberException {
        SMSRestApi.canonicalPhoneNumber("+31612345");
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testTooLong() throws InvalidPhoneNumberException {
        SMSRestApi.canonicalPhoneNumber("+316123456789");
    }

    @Test()
    public void testDutch() throws InvalidPhoneNumberException {
        assertEquals("+31612345678", SMSRestApi.canonicalPhoneNumber("+31612345678"));
    }

    @Test()
    public void testGerman() throws InvalidPhoneNumberException {
        assertEquals("+4915112345678", SMSRestApi.canonicalPhoneNumber("+4915112345678"));
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testGermanNotMobile() throws InvalidPhoneNumberException {
        assertEquals("+493012345678", SMSRestApi.canonicalPhoneNumber("+493012345678"));
    }

    @Test()
    public void testSpain() throws InvalidPhoneNumberException {
        assertEquals("+34712345678", SMSRestApi.canonicalPhoneNumber("+34712345678"));
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testSpainNotMobile() throws InvalidPhoneNumberException {
        assertEquals("+34112345678", SMSRestApi.canonicalPhoneNumber("+34112345678"));
    }

    @Test()
    public void testFrance() throws InvalidPhoneNumberException {
        assertEquals("+33612345678", SMSRestApi.canonicalPhoneNumber("+33612345678"));
    }

    @Test(expected=InvalidPhoneNumberException.class)
    public void testUS() throws InvalidPhoneNumberException {
        SMSRestApi.canonicalPhoneNumber("+12015550123");
    }
}
