package foundation.privacybydesign.sms.common;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hmac {
  private final SecretKeySpec secretKey;

  public Hmac(byte[] key)  {
      if (key.length < 32) {
          throw new RuntimeException("key too short: must be at least 32 bytes");
      }
      this.secretKey = new SecretKeySpec(key, "HmacSHA256");
  }

  public String createHmac(String input) throws NoSuchAlgorithmException, InvalidKeyException {
      final Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(secretKey); // Initialize with the secret key
      final byte[] encodedHmac = hmac.doFinal(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encodedHmac);
  }
}
