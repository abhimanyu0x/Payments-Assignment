package dev.dodo.notifications;

import dev.dodo.http.*;
import dev.dodo.identity.Business;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WebhookController {
  public record Register(@NotBlank @Size(max = 2048) String url) {}

  private final JdbcTemplate jdbc;
  private final SecretCipher cipher;
  private final Pages pages;
  private final Json json;
  private final String allowedUrl;

  public WebhookController(
      JdbcTemplate jdbc,
      SecretCipher cipher,
      Pages pages,
      Json json,
      @Value("${app.webhook-url}") String allowedUrl) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.pages = pages;
    this.json = json;
    this.allowedUrl = allowedUrl;
  }

  @PostMapping("/webhook-endpoints")
  public ResponseEntity<?> register(HttpServletRequest r, @Valid @RequestBody Register body) {
    if (!allowedUrl.equals(body.url()))
      throw ApiError.invalid("For this local demo the URL must equal the configured WEBHOOK_URL.");
    byte[] secret = cipher.generate();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO notifications.webhook_endpoints(id,business_id,url,secret_ciphertext) VALUES"
            + " (?,?,?,?)",
        id,
        Business.from(r),
        body.url(),
        cipher.encrypt(secret));
    return ResponseEntity.created(URI.create("/api/v1/webhook-endpoints/" + id))
        .body(
            Map.of(
                "id",
                id,
                "url",
                body.url(),
                "signing_secret",
                Base64.getEncoder().encodeToString(secret),
                "active",
                true));
  }

  @GetMapping("/webhook-endpoints/{id}")
  public Object endpoint(HttpServletRequest r, @PathVariable UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT id,url,active,created_at FROM notifications.webhook_endpoints WHERE"
                + " business_id=? AND id=?",
            Business.from(r),
            id);
    if (rows.isEmpty()) throw ApiError.missing();
    return Rows.clean(rows.get(0));
  }

  @GetMapping("/webhook-endpoints")
  public Object endpoints(
      HttpServletRequest r,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return pages.list(
        "notifications.webhook_endpoints",
        "id,url,active,created_at",
        Business.from(r),
        "endpoints",
        "",
        List.of(),
        limit,
        cursor);
  }

  @GetMapping("/webhook-deliveries")
  public Object deliveries(
      HttpServletRequest r,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return pages.list(
        "notifications.webhook_deliveries",
        "id,event_id,endpoint_id,status,attempt_count,next_attempt_at,last_http_status,last_error_code,created_at,delivered_at",
        Business.from(r),
        "deliveries",
        "",
        List.of(),
        limit,
        cursor);
  }

  @GetMapping("/events")
  public Object events(
      HttpServletRequest r,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    var page =
        pages.list(
            "notifications.events",
            "id,event_type,payload,created_at",
            Business.from(r),
            "events",
            "",
            List.of(),
            limit,
            cursor);
    @SuppressWarnings("unchecked")
    var rows = (List<Map<String, Object>>) page.get("data");
    for (var row : rows) row.put("payload", json.read(row.get("payload").toString()));
    return page;
  }
}
