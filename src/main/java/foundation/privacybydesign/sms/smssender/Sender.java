package foundation.privacybydesign.sms.smssender;

import java.io.IOException;

/**
 * Created by ayke on 17-8-17.
 */
public interface Sender {
    // TODO in Java 8: make this method static
    void send(String phone, String token) throws IOException;
}
