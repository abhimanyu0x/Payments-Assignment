package dev.dodo.payments;

import dev.dodo.billing.InvoicePayments;
import dev.dodo.http.*;
import dev.dodo.identity.Business;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
  public record Pay(
      @NotBlank
          @Size(max = 128)
          @Pattern(regexp = "tok_(success|insufficient_funds|card_declined|timeout|network_error)")
          String cardToken) {}

  private static final String FIELDS =
      "id,invoice_id,status,amount_cents,'USD' AS"
          + " currency,psp_reference,failure_code,last_error_code,review_required,created_at,completed_at";
  private final PaymentService service;
  private final JdbcTemplate jdbc;
  private final Json json;
  private final Pages pages;
  private final InvoicePayments invoices;

  public PaymentController(
      PaymentService service, JdbcTemplate jdbc, Json json, Pages pages, InvoicePayments invoices) {
    this.invoices = invoices;
    this.service = service;
    this.jdbc = jdbc;
    this.json = json;
    this.pages = pages;
  }

  @PostMapping("/invoices/{id}/pay")
  public ResponseEntity<String> pay(
      HttpServletRequest r,
      @PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody Pay body) {
    String response = service.accept(Business.from(r), id, key, body.cardToken());
    @SuppressWarnings("unchecked")
    var parsed = (Map<String, Object>) json.read(response);
    return ResponseEntity.accepted()
        .header("Location", parsed.get("status_url").toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  }

  @GetMapping("/payment-attempts/{id}")
  public Object get(HttpServletRequest r, @PathVariable UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT " + FIELDS + " FROM payments.payment_attempts WHERE business_id=? AND id=?",
            Business.from(r),
            id);
    if (rows.isEmpty()) throw ApiError.missing();
    return Rows.clean(rows.get(0));
  }

  @GetMapping("/invoices/{id}/payment-attempts")
  public Object history(
      HttpServletRequest r,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    invoices.requireExists(Business.from(r), id);
    return pages.list(
        "payments.payment_attempts",
        FIELDS,
        Business.from(r),
        "attempts:" + id,
        " AND invoice_id=?",
        List.of(id),
        limit,
        cursor);
  }
}
