
package foundation.privacybydesign.sms.tokens;

import foundation.privacybydesign.sms.SMSConfiguration;

public class TokenRequest {
    String token;
    int tries;
    long created;

    TokenRequest(String token, int tries, long created) {
        this.token = token;
        this.tries = tries;
        this.created = created;
    }

    TokenRequest(String token) {
        this.token = token;
        created = System.currentTimeMillis();
        tries = 0;
   }

    static boolean isExpiredForCreationDate(long creationDate) {
        return System.currentTimeMillis() - creationDate > SMSConfiguration.getInstance().getSMSTokenValidity() * 1000;
    }

    boolean isExpired() {
        return isExpiredForCreationDate(created);
    }
}
