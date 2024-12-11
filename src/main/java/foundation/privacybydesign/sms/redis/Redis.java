package foundation.privacybydesign.sms.redis;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisSentinelPool;

public class Redis {
    final private static Logger LOG = LoggerFactory.getLogger(Redis.class);
    final private static String KEY_PREFIX = System.getenv("REDIS_KEY_PREFIX") + ":";

    public static String createNamespace(String namespace) {
        return KEY_PREFIX + namespace + ":";
    }

    public static String createKey(String namespace, String key) {
        return createNamespace(namespace) + key + ":";
    }

    public static JedisSentinelPool createSentinelPoolFromEnv() {
        final Config redisConfig = configFromEnv();
        HostAndPort address = new HostAndPort(redisConfig.host, redisConfig.port);
        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .ssl(false)
                .user(redisConfig.username)
                .password(redisConfig.password)
                .build();
        return new JedisSentinelPool(redisConfig.masterName, Set.of(address), config, config);
    }

    public static class Config {
        String host;
        int port;
        String username;
        String masterName;
        String password;

        Config(String host, int port, String masterName, String username, String password) {
            this.host = host;
            this.port = port;
            this.masterName = masterName;
            this.username = username;
            this.password = password;
        }
    }

    static Config configFromEnv() {
        String host = System.getenv("REDIS_HOST");

        try {
            int port = Integer.parseInt(System.getenv("REDIS_PORT"));
            String username = System.getenv("REDIS_USERNAME");
            String password = System.getenv("REDIS_PASSWORD");
            String masterName = System.getenv("REDIS_MASTER_NAME");
            return new Config(host, port, masterName, username, password);
        } catch (NumberFormatException e) {
            LOG.error("failed to parse port as number: " + e.getMessage());
            return null;
        }
    }

}
