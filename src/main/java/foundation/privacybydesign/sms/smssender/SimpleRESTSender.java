package foundation.privacybydesign.sms.smssender;

import foundation.privacybydesign.sms.SMSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple REST implementation of an SMS sender.
 * Currently made for the "StartHere SMS Gateway App" but it could easily be
 * changed/extended for any other SMS gateway app based on REST.
 */
public class SimpleRESTSender implements Sender {
    private static final Logger logger = LoggerFactory.getLogger(SimpleRESTSender.class);

    public void send(String phone, String token) throws IOException {
        // Send the SMS token
        // https://stackoverflow.com/a/35013372/559350
        // TODO abstract this away
        Map<String, String> arguments = new HashMap<>();
        arguments.put("number", phone);
        // TODO: add URL to verify on the phone itself (if possible in 160
        // chars)
        arguments.put("message", "IRMA verify token: " + token);
        arguments.put("token", SMSConfiguration.getInstance().getSMSSenderToken());
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
        byte[] out = builder.toString().getBytes(StandardCharsets.UTF_8);

        String senderAddress = SMSConfiguration.getInstance().getSMSSenderAddress();
        OutputStream os = null;
        try {
            URL url = new URL(senderAddress);
            URLConnection con = url.openConnection();

            HttpURLConnection http = (HttpURLConnection) con;
            try {
                http.setRequestMethod("POST");
            } catch (ProtocolException e) {
                // unreachable
                throw new RuntimeException("cannot set POST as request method?");
            }
            http.setDoOutput(true);

            http.setFixedLengthStreamingMode(out.length);
            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            http.connect();
            os = http.getOutputStream();
            os.write(out);
        } catch (MalformedURLException e) {
            // Configuration error.
            if (senderAddress.length() == 0) {
                logger.error("Empty REST URL for SMS API");
            } else {
                logger.error("Invalid REST URL for SMS API: " + senderAddress);
            }
            // IOException will be shown to the user as "failed to send SMS"
            // which is mostly true.
            throw new IOException();
        } finally {
            if (os != null) {
                // The connection was opened.
                try {
                    // Try to close it.
                    os.close();
                } catch (IOException e) {
                    // We did our best, but if it fails, just give up...
                }
            }
        }
    }
}
