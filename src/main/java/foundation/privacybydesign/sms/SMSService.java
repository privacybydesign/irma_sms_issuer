package foundation.privacybydesign.sms;

import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

/**
 * Boilerplate: set up the REST API in SMSRestApi.
 */
@ApplicationPath("/")
public class SMSService extends ResourceConfig {
    public SMSService() {
        register(SMSRestApi.class);
    }
}
