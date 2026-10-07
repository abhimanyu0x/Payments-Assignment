package dev.dodo.http;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
  public static Map<String, Object> body(String code, String message, Object requestId) {
    return Map.of(
        "error", Map.of("code", code, "message", message, "request_id", String.valueOf(requestId)));
  }

  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> business(ApiError e, HttpServletRequest r) {
    return ResponseEntity.status(e.status)
        .body(body(e.code, e.getMessage(), r.getAttribute("requestId")));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    var details =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of(
                        "field",
                        f.getField(),
                        "message",
                        Objects.requireNonNullElse(f.getDefaultMessage(), "Invalid value")))
            .toList();
    return ResponseEntity.unprocessableEntity()
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    "validation_failed",
                    "message",
                    "One or more fields are invalid.",
                    "request_id",
                    String.valueOf(r.getAttribute("requestId")),
                    "details",
                    details)));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> malformed(Exception e, HttpServletRequest r) {
    return ResponseEntity.badRequest()
        .body(
            body(
                "invalid_request",
                "Malformed request or invalid parameter.",
                r.getAttribute("requestId")));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<?> database(DataAccessException e, HttpServletRequest r) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error(
            "database_failure request_id={} type={}",
            r.getAttribute("requestId"),
            e.getClass().getSimpleName());
    return ResponseEntity.status(503)
        .body(
            body(
                "temporarily_unavailable",
                "Please retry later. Preserve the payment idempotency key.",
                r.getAttribute("requestId")));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<?> notFound(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(404)
        .body(body("not_found", "Resource not found.", r.getAttribute("requestId")));
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> method(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(405)
        .body(
            body("method_not_allowed", "HTTP method not supported.", r.getAttribute("requestId")));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> media(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(415)
        .body(body("unsupported_media_type", "Use application/json.", r.getAttribute("requestId")));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e, HttpServletRequest r) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error(
            "unexpected_failure request_id={} type={}",
            r.getAttribute("requestId"),
            e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(body("internal_error", "An unexpected error occurred.", r.getAttribute("requestId")));
  }
}
