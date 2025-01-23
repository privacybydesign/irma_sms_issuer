package foundation.privacybydesign.sms.common;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Sha256Hasher {
  private final SecretKeySpec secretKey;

  public Sha256Hasher(byte[] key) {
      this.secretKey = new SecretKeySpec(key, "HmacSHA256");
  }

  public String CreateHash(String input) throws NoSuchAlgorithmException, InvalidKeyException {
      final Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(secretKey); // Initialize with the secret key
      final byte[] encodedHash = hmac.doFinal(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encodedHash);
  }
}
