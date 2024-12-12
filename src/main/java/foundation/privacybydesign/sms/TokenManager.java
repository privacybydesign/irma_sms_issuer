package foundation.privacybydesign.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import foundation.privacybydesign.sms.redis.Redis;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.*;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Generate and verify tokens sent in the SMS message.
 * A token is locked to an IP address and a phone number.
 */
public class TokenManager {
    static private TokenManager instance;
    private static final Logger LOG = LoggerFactory.getLogger(TokenManager.class);

    // Map to store sent tokens.
    // Format: {"phone": TokenRequest}
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

    public String generate(String phone) {
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
        tokenRepo.store(phone, new TokenRequest(token));
        return token;
    }

    public boolean verify(String phone, String token) {
        TokenRequest tr = tokenRepo.retrieve(phone);
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
        tokenRepo.remove(phone);
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

    void periodicCleanup() {
        tokenRepo.removeExpired();
    }
}

class TokenRequest {
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

/**
 * An interface for a repository to store tokens in.
 */
interface TokenRequestRepository {
    void store(String phone, TokenRequest request);

    TokenRequest retrieve(String phone);

    void remove(String phone);

    void removeExpired();
}

/**
 * A token repository that stores and retrieves tokens from a redis store.
 * Useful for when the sms issuer needs to be stateless.
 */
class RedisTokenRequestRepository implements TokenRequestRepository {
    private static Logger logger = LoggerFactory.getLogger(RedisTokenRequestRepository.class);
    private static String namespace = "request";
    JedisSentinelPool pool;

    RedisTokenRequestRepository() {
        pool = Redis.createSentinelPoolFromEnv();
    }

    @Override
    public void store(String phone, TokenRequest request) {
        final String key = Redis.createKey(namespace, phone);
        try (var jedis = pool.getResource()) {
            jedis.hset(key, "token", request.token);
            jedis.hset(key, "tries", Integer.toString(request.tries));
            jedis.hset(key, "created", String.valueOf(request.created));
        }
    }

    @Override
    public void remove(String phone) {
        final String key = Redis.createKey(namespace, phone);

        try (var jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    @Override
    public void removeExpired() {
        final String pattern = Redis.createNamespace(namespace) + "*";
        ScanParams scanParams = new ScanParams().match(pattern);
        String cursor = "0";

        try (var jedis = pool.getResource()) {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();
                cursor = scanResult.getCursor();

                for (String key : keys) {
                    removeIfExpired(jedis, key);
                }
            } while (!cursor.equals("0")); // continue until the cursor wraps around
        }
    }

    private void removeIfExpired(Jedis jedis, String key) {
        String createdStr = jedis.hget(key, "created");
        if (createdStr != null) {
            try {
                long created = Long.parseLong(createdStr);
                if (TokenRequest.isExpiredForCreationDate(created)) {
                    jedis.del(key);
                }
            } catch (NumberFormatException e) {
                logger.error("Failed to parse " + key + " creation into long: " + e.getMessage());
            }
        }
    }

    @Override
    public TokenRequest retrieve(String phone) {
        try (var jedis = pool.getResource()) {
            final String key = Redis.createKey(namespace, phone);
            try {
                String token = jedis.hget(key, "token");
                int tries = Integer.parseInt(jedis.hget(key, "tries"));
                long created = Long.parseLong(jedis.hget(key, "created"));
                return new TokenRequest(token, tries, created);
            } catch (NumberFormatException e) {
                logger.error("failed to parse for " + key + ": " + e.getMessage());
                return null;
            }
        }
    }
}

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
