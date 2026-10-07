package dev.dodo.payments;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentAttemptRepository {
  private final JdbcTemplate jdbc;
  private final int leaseSeconds;

  public PaymentAttemptRepository(
      JdbcTemplate jdbc, @Value("${app.payment-lease-seconds}") int leaseSeconds) {
    this.jdbc = jdbc;
    this.leaseSeconds = leaseSeconds;
  }

  public Map<String, Object> byKey(UUID business, String key) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM payments.payment_attempts WHERE business_id=? AND idempotency_key=?",
            business,
            key);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public boolean unresolved(UUID invoice) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM payments.payment_attempts WHERE invoice_id=? AND status IN"
                + " ('pending','unknown'))",
            Boolean.class,
            invoice));
  }

  public void insert(
      UUID id,
      UUID business,
      UUID invoice,
      String key,
      String fingerprint,
      String token,
      long amount,
      String response,
      int budget) {
    jdbc.update(
        "INSERT INTO"
            + " payments.payment_attempts(id,business_id,invoice_id,idempotency_key,request_fingerprint,mock_card_token,amount_cents,psp_operation_id,status,accepted_response,recovery_deadline_at)"
            + " VALUES (?,?,?,?,?,?,?,?,'pending',?,now()+make_interval(secs=>?))",
        id,
        business,
        invoice,
        key,
        fingerprint,
        token,
        amount,
        id,
        response,
        budget);
  }

  public Map<String, Object> lock(UUID business, UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM payments.payment_attempts WHERE business_id=? AND id=? FOR UPDATE",
            business,
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public Optional<Map<String, Object>> claim() {
    jdbc.update(
        "UPDATE payments.payment_attempts SET"
            + " status='unknown',review_required=true,last_error_code='recovery_budget_exhausted',updated_at=now()"
            + " WHERE status IN ('pending','unknown') AND NOT review_required AND"
            + " recovery_deadline_at<=now() AND (lease_expires_at IS NULL OR"
            + " lease_expires_at<=now())");
    var rows =
        jdbc.queryForList(
            "WITH job AS (SELECT id FROM payments.payment_attempts WHERE status IN"
                + " ('pending','unknown') AND NOT review_required AND recovery_deadline_at>now()"
                + " AND next_attempt_at<=now() AND (lease_expires_at IS NULL OR"
                + " lease_expires_at<=now()) ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED"
                + " LIMIT 1) UPDATE payments.payment_attempts p SET"
                + " claim_version=p.claim_version+1,"
                + " reconciliation_round_count=p.reconciliation_round_count+CASE WHEN"
                + " p.claim_version>0 THEN 1 ELSE 0 END,"
                + " lease_expires_at=now()+make_interval(secs=>?),updated_at=now() FROM job WHERE"
                + " p.id=job.id RETURNING p.*",
            leaseSeconds);
    return rows.stream().findFirst();
  }

  public void countCall(UUID id, long version) {
    jdbc.update(
        "UPDATE payments.payment_attempts SET processor_call_count=processor_call_count+1 WHERE"
            + " id=? AND claim_version=?",
        id,
        version);
  }

  public void complete(UUID id, String status, String ref, String failure) {
    jdbc.update(
        "UPDATE payments.payment_attempts SET"
            + " status=?,psp_reference=?,failure_code=?,completed_at=now(),updated_at=now(),lease_expires_at=NULL,last_error_code=NULL"
            + " WHERE id=?",
        status,
        ref,
        failure,
        id);
  }

  public void defer(UUID id, long version, String error, int delay, boolean exhausted) {
    jdbc.update(
        "UPDATE payments.payment_attempts SET"
            + " status='unknown',last_error_code=?,next_attempt_at=now()+make_interval(secs=>?),lease_expires_at=NULL,review_required=?,updated_at=now()"
            + " WHERE id=? AND claim_version=? AND status IN ('pending','unknown')",
        error,
        delay,
        exhausted,
        id,
        version);
  }
}
