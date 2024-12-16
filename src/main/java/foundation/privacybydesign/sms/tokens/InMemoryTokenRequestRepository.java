package foundation.privacybydesign.sms.tokens;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token repository that stores tokens in RAM.
 * Should not be used when the sms issuer needs to be stateless.
 */
class InMemoryTokenRequestRepository implements TokenRequestRepository {
    private final Map<String, TokenRequest> tokenMap = new ConcurrentHashMap<>();

    @Override
    public void store(String phone, TokenRequest request) {
        tokenMap.put(phone, request);
    }

    @Override
    public TokenRequest retrieve(String phone) {
        return tokenMap.get(phone);
    }

    @Override
    public void remove(String phone) {
        tokenMap.remove(phone);
    }

    @Override
    public void removeExpired() {
        // Use enhanced for loop, because an iterator makes sure concurrency issues
        // cannot occur.
        for (Map.Entry<String, TokenRequest> entry : tokenMap.entrySet()) {
            if (entry.getValue().isExpired()) {
                tokenMap.remove(entry.getKey());
            }
        }
    }
}
