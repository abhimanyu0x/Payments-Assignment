package dev.dodo.identity;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class KeyHash {
  private KeyHash() {}

  public static String of(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
