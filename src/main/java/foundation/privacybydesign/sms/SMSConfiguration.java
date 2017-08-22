package foundation.privacybydesign.sms;

import foundation.privacybydesign.common.BaseConfiguration;
import io.jsonwebtoken.SignatureAlgorithm;
import org.irmacard.api.common.util.GsonUtil;

import java.io.IOException;

/**
 * Configuration manager. The config itself is stored in config.json, which
 * is probably located in build/resources/main.
 */
public class SMSConfiguration extends BaseConfiguration {
    static SMSConfiguration instance;
    static final String CONFIG_FILENAME = "config.json";

    private String sms_sender_backend = "";
    private String sms_sender_ssh_host = "";
    private String sms_sender_ssh_host_rsa_key = "";
    private String sms_sender_ssh_user = "";
    private String sms_sender_ssh_key_path = "";
    private String sms_sender_ssh_key_passphrase = "";
    private String sms_sender_address = "";
    private String sms_sender_param_phone = "";
    private String sms_sender_param_message = "";
    private String sms_sender_token = "";
    private long token_validity = 0;
    private String sms_prefix = "";
    private String server_name = "";
    private String human_readable_name = "";
    private String scheme_manager = "";
    private String sms_issuer = "";
    private String sms_credential = "";
    private String sms_attribute = "";

    public static SMSConfiguration getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            String json = new String(getResource(CONFIG_FILENAME));
            instance = GsonUtil.getGson().fromJson(json, SMSConfiguration.class);
        } catch (IOException e) {
            instance = new SMSConfiguration();
        }
    }

    public String getSMSSenderBackend() {return sms_sender_backend; }

    public String getSMSSenderHost() {return sms_sender_ssh_host; }

    public String getSMSSenderHostRsaKey() {return sms_sender_ssh_host_rsa_key; }

    public String getSMSSenderUser() {return sms_sender_ssh_user; }

    public String getSMSSenderKeyPath() {return sms_sender_ssh_key_path; }

    public String getSMSSenderKeyPassphrase() {return sms_sender_ssh_key_passphrase; }

    public String getSMSSenderAddress() {return sms_sender_address; }

    public String getSMSSenderParamPhone() { return sms_sender_param_phone; }

    public String getSMSSenderParamMessage() { return sms_sender_param_message; }

    public String getSMSSenderToken() { return sms_sender_token; }

    public long getSMSTokenValidity() { return token_validity; }

    public String getSMSPrefix() { return sms_prefix; }

    public String getServerName() { return server_name; }

    public String getHumanReadableName() { return human_readable_name; }

    public String getSchemeManager() { return scheme_manager; }

    public String getSMSIssuer() { return sms_issuer; }

    public String getSMSCredential() { return sms_credential; }

    public String getSMSAttribute() { return sms_attribute; }

    public SignatureAlgorithm getJwtAlgorithm() { return SignatureAlgorithm.RS256; }
}

