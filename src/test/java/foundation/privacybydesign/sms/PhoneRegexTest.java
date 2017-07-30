package foundation.privacybydesign.sms;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test various valid and invalid phone numbers.
 */
public class PhoneRegexTest {
    @Test
    public void testValidPhone1() {
        assertTrue("valid number failed validation: 0612345678",
                "0612345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testValidPhone2() {
        assertTrue("valid number failed validation: +31612345678",
                "+31612345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testValidPhone3() {
        assertTrue("valid number failed validation: 0031612345678",
                "0031612345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testWrongPrefix() {
        assertFalse("number with the wrong prefix was accepted",
                "0712345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testZeroStart() {
        assertFalse("number with a 0 after the prefix was accepted",
                "0602345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testEmpty() {
        assertFalse("empty phone number was accepted",
                "".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testTooShort() {
        assertFalse("too short phone number was accepted",
                "061234567".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testTooLong() {
        assertFalse("too long phone number was accepted",
                "06123456789".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testWhitespace1() {
        assertFalse("number with whitespace was accepted",
                "06 12345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testWhitespace2() {
        assertFalse("number with whitespace was accepted",
                " 0612345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testWhitespace3() {
        assertFalse("number with whitespace was accepted",
                "0612345678 ".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testNewline1() {
        assertFalse("number with newline was accepted",
                "0612345678\n".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testNewline2() {
        assertFalse("number with newline was accepted",
                "\n0612345678".matches(SMSRestApi.PHONE_PATTERN));
    }

    @Test
    public void testNullChar() {
        assertFalse("number with null char was accepted",
                "0612345678\0".matches(SMSRestApi.PHONE_PATTERN));
    }
}
