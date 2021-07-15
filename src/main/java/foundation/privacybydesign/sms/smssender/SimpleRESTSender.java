package foundation.privacybydesign.sms.smssender;

import foundation.privacybydesign.sms.SMSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;

/**
 * Simple REST implementation of an SMS sender.
 * Currently made for the "StartHere SMS Gateway App" but it could easily be
 * changed/extended for any other SMS gateway app based on REST.
 */
public class SimpleRESTSender extends RESTSender {
    private static final Logger logger = LoggerFactory.getLogger(SimpleRESTSender.class);

    @Override
    public void sendBytes(String phone, byte[] out) throws IOException {
        SMSConfiguration conf = SMSConfiguration.getInstance();
        String senderAddress = conf.getSMSSenderAddress();
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
            http.setConnectTimeout(conf.getSMSSenderTimeout());
            http.setReadTimeout(conf.getSMSSenderTimeout());
            http.setDoOutput(true);
            http.setFixedLengthStreamingMode(out.length);
            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            http.connect();
            os = http.getOutputStream();
            os.write(out); // unbuffered, so no need to flush
            int code = http.getResponseCode();
            if (code != 200) {
                throw new IOException("Expected HTTP REST status code 200, but got " + http.getResponseMessage());
            }
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
