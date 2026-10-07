package dev.dodo.payments;

import dev.dodo.billing.InvoicePayments;
import dev.dodo.http.*;
import dev.dodo.identity.KeyHash;
import dev.dodo.notifications.WebhookEvents;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
  private final InvoicePayments invoices;
  private final PaymentAttemptRepository attempts;
  private final TransactionTemplate tx;
  private final Json json;
  private final WebhookEvents events;
  private final int recoverySeconds;
  private final int[] delays;

  public PaymentService(
      InvoicePayments invoices,
      PaymentAttemptRepository attempts,
      TransactionTemplate tx,
      Json json,
      WebhookEvents events,
      @Value("${app.payment-recovery-seconds}") int recoverySeconds,
      @Value("${app.payment-retry-delays}") String delays) {
    this.invoices = invoices;
    this.attempts = attempts;
    this.tx = tx;
    this.json = json;
    this.events = events;
    this.recoverySeconds = recoverySeconds;
    this.delays = Arrays.stream(delays.split(",")).mapToInt(Integer::parseInt).toArray();
  }

  public String accept(UUID business, UUID invoice, String key, String token) {
    if (key == null || !key.matches("[!-~]{1,128}"))
      throw new ApiError(
          400,
          "idempotency_key_required",
          "Provide an Idempotency-Key with 1-128 visible ASCII characters.");
    String fingerprint = KeyHash.of("pay|" + invoice + "|" + token);
    try {
      return tx.execute(
          s -> {
            var inv = invoices.lock(business, invoice);
            var previous = attempts.byKey(business, key);
            if (previous != null) return replay(previous, fingerprint);
            invoices.requirePayable(inv, attempts.unresolved(invoice));
            UUID id = UUID.randomUUID();
            String response =
                json.write(
                    Map.of(
                        "payment_attempt_id",
                        id,
                        "invoice_id",
                        invoice,
                        "status",
                        "pending",
                        "status_url",
                        "/api/v1/payment-attempts/" + id));
            attempts.insert(
                id,
                business,
                invoice,
                key,
                fingerprint,
                token,
                ((Number) inv.get("total_amount_cents")).longValue(),
                response,
                recoverySeconds);
            return response;
          });
    } catch (DuplicateKeyException e) {
      var previous = attempts.byKey(business, key);
      if (previous != null) return replay(previous, fingerprint);
      throw ApiError.conflict("payment_in_progress", "Another payment was accepted concurrently.");
    }
  }

  private String replay(Map<String, Object> previous, String fingerprint) {
    if (!fingerprint.equals(previous.get("request_fingerprint")))
      throw ApiError.conflict(
          "idempotency_key_conflict", "This key was already used with a different request.");
    return previous.get("accepted_response").toString();
  }

  public void finish(Map<String, Object> claim, PaymentProcessorClient.Result result) {
    tx.executeWithoutResult(
        s -> {
          UUID id = (UUID) claim.get("id"),
              business = (UUID) claim.get("business_id"),
              invoice = (UUID) claim.get("invoice_id");
          invoices.lock(business, invoice);
          var current = attempts.lock(business, id);
          if (current == null
              || !Objects.equals(current.get("claim_version"), claim.get("claim_version"))
              || !List.of("pending", "unknown").contains(current.get("status"))) return;
          if (result.status().equals("succeeded") || result.status().equals("failed")) {
            attempts.complete(id, result.status(), result.reference(), result.failure());
            if (result.status().equals("succeeded")) invoices.markPaid(business, invoice);
            var data = new LinkedHashMap<String, Object>();
            data.put("invoice_id", invoice);
            data.put("payment_attempt_id", id);
            data.put("state", result.status().equals("succeeded") ? "paid" : "open");
            data.put("total_amount_cents", current.get("amount_cents"));
            data.put("currency", "USD");
            if (result.failure() != null) data.put("failure_code", result.failure());
            events.record(
                business,
                result.status().equals("succeeded") ? "invoice.paid" : "invoice.payment_failed",
                result.status().equals("succeeded") ? invoice : id,
                data);
          } else {
            int round = ((Number) current.get("reconciliation_round_count")).intValue();
            boolean exhausted =
                round >= delays.length
                    || ((java.sql.Timestamp) current.get("recovery_deadline_at"))
                        .toInstant()
                        .isBefore(java.time.Instant.now());
            attempts.defer(
                id,
                ((Number) claim.get("claim_version")).longValue(),
                result.failure() == null ? "confirmation_pending" : result.failure(),
                delays[Math.min(round, delays.length - 1)],
                exhausted);
          }
        });
  }
}
