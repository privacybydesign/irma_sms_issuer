package foundation.privacybydesign.sms;

public class CMGatewayConfiguration {
    private String from = "";
    private String api_endpoint = "";
    private String product_token = "";
    private String reference = "";

    public String getFrom() {
        return from;
    }

    public String getApiEndpoint() {
        return api_endpoint;
    }

    public String getProductToken() {
        return product_token;
    }

    public String getReference() {
        return reference;
    }
}
