package foundation.privacybydesign.sms.smssender;

import foundation.privacybydesign.sms.SMSConfiguration;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Send a SMS message.
 * Subclasses can implement how the message is actually delivered.
 */
public abstract class Sender {
    protected byte[] getMessage(String phone, String token) {
        SMSConfiguration conf = SMSConfiguration.getInstance();

        // TODO: add URL to verify on the phone itself (if possible in 160
        // chars)
        String message = conf.getSMSPrefix() + token;

        // https://stackoverflow.com/a/35013372/559350
        Map<String, String> arguments = new HashMap<>();
        arguments.put(conf.getSMSSenderParamPhone(), phone);
        arguments.put(conf.getSMSSenderParamMessage(), message);
        arguments.put("token", conf.getSMSSenderToken()); // only used for one app
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            try {
                if (builder.length() != 0) {
                    builder.append("&");
                }
                builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                builder.append("=");
                builder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                // unreachable
                throw new RuntimeException("Invalid encoding?");
            }
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    abstract public void send(String phone, String token) throws IOException;
}
