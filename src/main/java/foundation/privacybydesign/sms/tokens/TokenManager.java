package foundation.privacybydesign.sms.tokens;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import foundation.privacybydesign.sms.common.Sha256Hasher;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Generate and verify tokens sent in the SMS message.
 * A token is locked to an IP address and a phone number.
 */
public class TokenManager {
    static private TokenManager instance;
    private static final Logger LOG = LoggerFactory.getLogger(TokenManager.class);

    private final TokenRequestRepository tokenRepo;
    private final SecureRandom random;

    public TokenManager() {
        final String storageType = System.getenv("STORAGE_TYPE");
        if (storageType.equals("redis")) {
            LOG.info("using Redis token request repository");
            tokenRepo = new RedisTokenRequestRepository();
        } else {
            LOG.info("using InMemory token request repository");
            tokenRepo = new InMemoryTokenRequestRepository();
        }
        random = new SecureRandom();
    }

    public static TokenManager getInstance() {
        if (instance == null) {
            instance = new TokenManager();
        }
        return instance;
    }

    public String generate(String phone, Sha256Hasher hmac) throws Exception {
        final String phoneHash = hmac.CreateHash(phone);
        
        // https://stackoverflow.com/a/41156/559350
        // There are 30 bits. Using 32 possible values per char means
        // every char consumes exactly 5 bits, thus a token is 6 bytes.
        String token = new BigInteger(30, random).toString(32).toUpperCase();
        // ...but when the BigInteger is < 2**25, the first char is 0 and is
        // thus ignored. Add it here at the start (humans count in big endian).
        while (token.length() < 6) {
            token = "0" + token;
        }
        // With a toString of 32, there are 4 alphanumeric chars left: WXYZ.
        // Replace easy to confuse chars with those chars.
        token = token.replace('0', 'W');
        token = token.replace('O', 'X');
        token = token.replace('1', 'Y');
        token = token.replace('I', 'Z');
        tokenRepo.store(phoneHash, new TokenRequest(token));
        return token;
    }

    public boolean verify(String phone, String token, Sha256Hasher hmac)
            throws NoSuchAlgorithmException, InvalidKeyException {
        final String phoneHash = hmac.CreateHash(phone);
        TokenRequest tr = tokenRepo.retrieve(phoneHash);
        if (tr == null) {
            LOG.error("Phone number not found");
            return false;
        }

        if (tr.isExpired()) {
            // Expired, but not yet cleaned out by periodicCleanup()
            LOG.error("Token expired");
            return false;
        }
        if (!isEqualsConstantTime(tr.token.toCharArray(), token.toCharArray())) {
            tr.tries++;
            LOG.error("Token is wrong");
            return false;
        }
        if (tr.tries > 3) {
            LOG.error("Token was tried to validate too often");
            // User may try at most 3 times, it shouldn't be that hard.
            // TODO: report this error back to the user.
            return false;
        }
        tokenRepo.remove(phoneHash);
        return true;
    }

    /**
     * Compare two byte arrays in constant time.
     */
    public static boolean isEqualsConstantTime(char[] a, char[] b) {
        if (a.length != b.length) {
            return false;
        }

        byte result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    public void periodicCleanup() {
        tokenRepo.removeExpired();
    }
}