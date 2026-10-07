package dev.dodo.notifications;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {
  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  public SecretCipher(@Value("${app.encryption-key}") String encoded) {
    key = Base64.getDecoder().decode(encoded);
    if (key.length != 32)
      throw new IllegalArgumentException("Encryption key must contain 32 bytes.");
  }

  public byte[] generate() {
    byte[] secret = new byte[32];
    random.nextBytes(secret);
    return secret;
  }

  public String encrypt(byte[] secret) {
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      byte[] encrypted = c.doFinal(secret);
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(nonce.length + encrypted.length)
                  .put(nonce)
                  .put(encrypted)
                  .array());
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encrypt secret", e);
    }
  }

  public byte[] decrypt(String encoded) {
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      if (all.length < 28) throw new IllegalArgumentException();
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12)));
      return c.doFinal(Arrays.copyOfRange(all, 12, all.length));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot decrypt secret", e);
    }
  }
}
