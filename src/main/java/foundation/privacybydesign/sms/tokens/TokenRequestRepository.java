
package foundation.privacybydesign.sms.tokens;

/**
 * An interface for a repository to store tokens in.
 */
public interface TokenRequestRepository {
    void store(String phone, TokenRequest request);

    /**
     * Retrieve the token request corresponding to the provided phone number.
     * Should return null when there's no request found.
     */
    TokenRequest retrieve(String phone);

    void remove(String phone);

    void removeExpired();
}

