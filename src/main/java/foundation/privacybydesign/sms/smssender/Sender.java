package foundation.privacybydesign.sms.smssender;

import foundation.privacybydesign.sms.SMSConfiguration;

import java.io.IOException;
import java.util.Formatter;

/**
 * Send a SMS message.
 * Subclasses can implement how the message is actually delivered.
 */
public abstract class Sender {
    private String getMessage(String language, String phone, String token) {
        SMSConfiguration conf = SMSConfiguration.getInstance();

        Formatter formatter = new Formatter();
        return formatter.format(conf.getSMSTemplate(language),
                token, phone + ":" + token)
                .toString();
    }

    abstract protected void sendMessage(String phone, String message) throws IOException;

    public void send(String language, String phone, String token) throws IOException {
        String message = getMessage(language, phone, token);
        sendMessage(phone, message);
    }
}
