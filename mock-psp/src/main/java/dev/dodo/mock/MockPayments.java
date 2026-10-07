package dev.dodo.mock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MockPayments {
  public record Payment(
      @NotNull UUID operation_id,
      @NotNull @Min(1) @Max(1000000000000L) Long amount_cents,
      @NotNull @Pattern(regexp = "USD") String currency,
      @NotNull
          @Pattern(regexp = "tok_(success|insufficient_funds|card_declined|timeout|network_error)")
          String card_token) {}

  private final JdbcTemplate jdbc;

  public MockPayments(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostMapping("/payments")
  public ResponseEntity<?> pay(@Valid @RequestBody Payment input) throws Exception {
    String fingerprint =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        (input.amount_cents() + "|" + input.currency() + "|" + input.card_token())
                            .getBytes(StandardCharsets.UTF_8)));
    boolean network = input.card_token().equals("tok_network_error");
    int delay = input.card_token().equals("tok_timeout") ? 30000 : 100;
    jdbc.update(
        "INSERT INTO"
            + " mock_psp.operations(operation_id,request_fingerprint,amount_cents,currency,card_token,status,failure_code,complete_after,completed_at)"
            + " VALUES (?,?,?,?,?,?,?,now()+(? * interval '1 millisecond'),CASE WHEN ? THEN now()"
            + " ELSE NULL END) ON CONFLICT(operation_id) DO UPDATE SET"
            + " post_count=mock_psp.operations.post_count+1",
        input.operation_id(),
        fingerprint,
        input.amount_cents(),
        input.currency(),
        input.card_token(),
        network ? "failed" : "pending",
        network ? "processor_error" : null,
        delay,
        network);
    var operation = load(input.operation_id());
    if (!fingerprint.equals(operation.get("request_fingerprint")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Operation parameters changed");
    if (network)
      return ResponseEntity.internalServerError().body(Map.of("error", "processor_unavailable"));
    long wait =
        Math.max(
            0,
            ((java.sql.Timestamp) operation.get("complete_after")).toInstant().toEpochMilli()
                - System.currentTimeMillis());
    if (wait > 0) Thread.sleep(wait + 1);
    completeDue();
    return ResponseEntity.ok(result(load(input.operation_id())));
  }

  @GetMapping("/payments/{id}")
  public Object get(@PathVariable UUID id) {
    completeDue();
    return result(load(id));
  }

  @Scheduled(fixedDelay = 250)
  public void completeDue() {
    jdbc.update(
        "UPDATE mock_psp.operations SET status=CASE WHEN card_token IN"
            + " ('tok_card_declined','tok_insufficient_funds') THEN 'failed' ELSE 'succeeded'"
            + " END,failure_code=CASE WHEN card_token='tok_card_declined' THEN 'card_declined' WHEN"
            + " card_token='tok_insufficient_funds' THEN 'insufficient_funds' ELSE NULL"
            + " END,psp_ref=CASE WHEN card_token IN ('tok_success','tok_timeout') THEN operation_id"
            + " ELSE NULL END,completed_at=now() WHERE status='pending' AND complete_after<=now()");
  }

  private Map<String, Object> load(UUID id) {
    var rows = jdbc.queryForList("SELECT * FROM mock_psp.operations WHERE operation_id=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.get(0);
  }

  private Map<String, Object> result(Map<String, Object> row) {
    var out = new LinkedHashMap<String, Object>();
    out.put("status", row.get("status"));
    if (row.get("psp_ref") != null) out.put("psp_ref", row.get("psp_ref"));
    if (row.get("failure_code") != null) out.put("code", row.get("failure_code"));
    return out;
  }
}
