package dev.dodo.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class Business {
  private Business() {}

  public static UUID from(HttpServletRequest request) {
    return (UUID) request.getAttribute("businessId");
  }
}
