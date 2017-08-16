package foundation.privacybydesign.sms.ratelimit;

/**
 * The entered phone number didn't validate.
 */
public class InvalidPhoneNumberException extends Exception {
    public InvalidPhoneNumberException() {
        super();
    }
}
