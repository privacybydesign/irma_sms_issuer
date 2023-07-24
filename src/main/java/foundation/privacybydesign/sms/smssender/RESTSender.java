package foundation.privacybydesign.sms.smssender;

import foundation.privacybydesign.sms.SMSConfiguration;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class RESTSender extends Sender {
    private byte[] getBytes(String phone, String message) {
        SMSConfiguration conf = SMSConfiguration.getInstance();

        // https://stackoverflow.com/a/35013372/559350
        Map<String, String> arguments = new HashMap<>();
        arguments.put(conf.getSMSSenderParamPhone(), phone);
        arguments.put(conf.getSMSSenderParamMessage(), message);
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

    abstract void sendBytes(byte[] out) throws IOException;

    @Override
    protected void sendMessage(String phone, String message) throws IOException {
        byte[] out = this.getBytes(phone, message);
        this.sendBytes(out);
    }
}
