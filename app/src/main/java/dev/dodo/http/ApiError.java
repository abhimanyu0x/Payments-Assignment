package dev.dodo.http;

public class ApiError extends RuntimeException {
  public final int status;
  public final String code;

  public ApiError(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiError missing() {
    return new ApiError(404, "not_found", "Resource not found.");
  }

  public static ApiError invalid(String message) {
    return new ApiError(422, "validation_failed", message);
  }

  public static ApiError conflict(String code, String message) {
    return new ApiError(409, code, message);
  }
}
