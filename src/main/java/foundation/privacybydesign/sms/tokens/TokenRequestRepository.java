
package foundation.privacybydesign.sms.tokens;

/**
 * An interface for a repository to store tokens in.
 */
public interface TokenRequestRepository {
    void store(String phone, TokenRequest request);

    TokenRequest retrieve(String phone);

    void remove(String phone);

    void removeExpired();
}

