package dev.dodo.notifications;

import dev.dodo.http.HttpCalls;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.workers-enabled", havingValue = "true")
public class WebhookWorker {
  private final JdbcTemplate jdbc;
  private final SecretCipher cipher;
  private final WebhookSigner signer;
  private final HttpCalls http;
  private final int[] delays;
  private final String allowedUrl;
  private final ExecutorService executor = Executors.newFixedThreadPool(4);
  private final Semaphore slots = new Semaphore(4);
  private volatile boolean stopping;

  public WebhookWorker(
      JdbcTemplate jdbc,
      SecretCipher cipher,
      WebhookSigner signer,
      HttpCalls http,
      @Value("${app.webhook-retry-delays}") String delays,
      @Value("${app.webhook-url}") String allowedUrl) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.signer = signer;
    this.http = http;
    this.delays = Arrays.stream(delays.split(",")).mapToInt(Integer::parseInt).toArray();
    this.allowedUrl = allowedUrl;
  }

  @Scheduled(fixedDelay = 1000)
  public void poll() {
    if (stopping) return;
    try {
      jdbc.update(
          "UPDATE notifications.webhook_deliveries SET status='exhausted',lease_expires_at=NULL"
              + " WHERE status='pending' AND (attempt_count>=6 OR delivery_deadline_at<=now()) AND"
              + " (lease_expires_at IS NULL OR lease_expires_at<=now())");
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("webhook_sweep_failed type={}", e.getClass().getSimpleName());
      return;
    }
    while (slots.tryAcquire()) {
      try {
        var rows =
            jdbc.queryForList(
                "WITH job AS (SELECT id FROM notifications.webhook_deliveries WHERE"
                    + " status='pending' AND attempt_count<6 AND delivery_deadline_at>now() AND"
                    + " next_attempt_at<=now() AND (lease_expires_at IS NULL OR"
                    + " lease_expires_at<=now()) ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED"
                    + " LIMIT 1) UPDATE notifications.webhook_deliveries d SET"
                    + " attempt_count=d.attempt_count+1,claim_version=d.claim_version+1,lease_expires_at=now()+interval"
                    + " '30 seconds' FROM job WHERE d.id=job.id RETURNING d.*");
        if (rows.isEmpty()) {
          slots.release();
          return;
        }
        executor.submit(
            () -> {
              try {
                deliver(rows.get(0));
              } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn(
                        "webhook_incomplete delivery_id={} type={}",
                        rows.get(0).get("id"),
                        e.getClass().getSimpleName());
              } finally {
                slots.release();
              }
            });
      } catch (Exception e) {
        slots.release();
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("webhook_claim_failed type={}", e.getClass().getSimpleName());
        return;
      }
    }
  }

  private void deliver(Map<String, Object> job) {
    var details =
        jdbc.queryForMap(
            "SELECT e.payload,w.url,w.secret_ciphertext,w.active FROM notifications.events e JOIN"
                + " notifications.webhook_endpoints w ON w.id=? WHERE e.id=?",
            job.get("endpoint_id"),
            job.get("event_id"));
    Integer status = null;
    String error = null;
    boolean success = false;
    try {
      if (!allowedUrl.equals(details.get("url")) || !Boolean.TRUE.equals(details.get("active")))
        throw new IllegalStateException("Endpoint disabled or disallowed");
      if (((java.sql.Timestamp) job.get("delivery_deadline_at"))
          .toInstant()
          .isBefore(Instant.now())) throw new IllegalStateException("Deadline exceeded");
      String body = details.get("payload").toString();
      long timestamp = Instant.now().getEpochSecond();
      String signature =
          signer.sign(cipher.decrypt(details.get("secret_ciphertext").toString()), timestamp, body);
      var request =
          HttpRequest.newBuilder(URI.create(details.get("url").toString()))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .header("X-Webhook-Id", job.get("event_id").toString())
              .header("X-Webhook-Timestamp", Long.toString(timestamp))
              .header("X-Webhook-Signature", "v1=" + signature)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      status = http.deliver(request);
      success = status >= 200 && status < 300;
      if (!success) error = "http_" + status;
    } catch (Exception e) {
      error = "delivery_error";
    }
    int attempt = ((Number) job.get("attempt_count")).intValue();
    int delay = delays[Math.min(attempt - 1, delays.length - 1)];
    String next = success ? "delivered" : attempt >= 6 ? "exhausted" : "pending";
    jdbc.update(
        "UPDATE notifications.webhook_deliveries SET"
            + " status=?,last_http_status=?,last_error_code=?,delivered_at=CASE WHEN ? THEN now()"
            + " ELSE NULL END,next_attempt_at=now()+make_interval(secs=>?),lease_expires_at=NULL"
            + " WHERE id=? AND claim_version=? AND status='pending'",
        next,
        status,
        error,
        success,
        delay,
        job.get("id"),
        job.get("claim_version"));
  }

  @PreDestroy
  public void close() throws InterruptedException {
    stopping = true;
    executor.shutdown();
    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
  }
}
