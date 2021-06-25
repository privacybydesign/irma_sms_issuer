package foundation.privacybydesign.sms.smssender;

import foundation.privacybydesign.sms.CMGatewayConfiguration;
import foundation.privacybydesign.sms.SMSConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Implements CM Gateway HTTP GET endpoint
 * https://www.cm.com/en-en/app/docs/api/business-messaging-api/1.0/index#http-get
 */
public class CMGatewaySender extends Sender {
    @Override
    protected void sendMessage(String phone, String message) throws IOException {
        CMGatewayConfiguration conf = SMSConfiguration.getInstance().getCMGatewayConfiguration();
        if (conf == null) {
            throw new IOException("CM gateway configuration not found");
        }

        // CM expects 00 to be used as international prefix, instead of the + prefix in the E164 phone number format.
        if (phone.startsWith("+")) {
            phone = String.format("00%s", phone.substring(1));
        } else if (!phone.startsWith("00")){
            throw new IOException("CM expects internationalized phone numbers");
        }

        String endpoint = conf.getApiEndpoint();
        if (!endpoint.startsWith("https://")) {
            throw new IOException("CM gateway API endpoint should use https");
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        Map<String,String> parameters = new TreeMap<>();
        parameters.put("producttoken", conf.getProductToken());
        parameters.put("from", conf.getFrom());
        parameters.put("to", phone);
        parameters.put("body", message);
        parameters.put("reference", conf.getReference());

        URL url = this.constructURL(String.format("%s/gateway.ashx", endpoint), parameters);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int responseStatus = connection.getResponseCode();
        if (responseStatus != 200) {
            throw new IOException(String.format("CM gateway returned status code %d", responseStatus));
        }

        InputStream responseBody = connection.getInputStream();
        String text;
        try (Scanner scanner = new Scanner(responseBody, StandardCharsets.UTF_8.name())) {
            text = scanner.useDelimiter("\\A").next();
        }

        // CM returns empty string when sending the SMS succeeds.
        if (text == null) {
            throw new IOException("CM response could not be parsed");
        } else if (!text.equals("")) {
            throw new IOException(String.format("Error response received from CM: %s", text));
        }
    }

    private URL constructURL(String endpoint, Map<String, String> parameters) throws MalformedURLException {
        StringBuilder builder = new StringBuilder(endpoint);
        builder.append("?");
        for (Map.Entry<String,String> parameter : parameters.entrySet()) {
            // We only need alphabetic url parameter keys, so we reject all others.
            if (!parameter.getKey().matches("^[a-z]*$")) {
                throw new MalformedURLException("Invalid URL parameters");
            }
            try {
                String value = URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8.name());
                builder.append(String.format("%s=%s&", parameter.getKey(), value));
            } catch (UnsupportedEncodingException _e) {
                // Encoding is hardcoded in the code, so this catch is unreachable.
                throw new RuntimeException("Invalid encoding?");
            }
        }
        // Remove final '&' character (or the '?' character if there are no parameters)
        builder.deleteCharAt(builder.length() - 1);
        return new URL(builder.toString());
    }
}
