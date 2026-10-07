package dev.dodo.billing;

import dev.dodo.customers.CustomerService;
import dev.dodo.http.*;
import dev.dodo.notifications.WebhookEvents;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InvoiceService implements InvoicePayments {
  private final JdbcTemplate jdbc;
  private final CustomerService customers;
  private final WebhookEvents events;
  private final TransactionTemplate tx;

  public InvoiceService(
      JdbcTemplate jdbc, CustomerService customers, WebhookEvents events, TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.customers = customers;
    this.events = events;
    this.tx = tx;
  }

  public Map<String, Object> preview(UUID business, InvoiceInput input) {
    customers.require(business, input.customerId());
    return Map.of("currency", "USD", "total_amount_cents", InvoiceTotal.calculate(input.items()));
  }

  public Map<String, Object> create(UUID business, InvoiceInput input) {
    return tx.execute(
        s -> {
          customers.require(business, input.customerId());
          long total = InvoiceTotal.calculate(input.items());
          UUID id = UUID.randomUUID();
          jdbc.update(
              "INSERT INTO billing.invoices(id,business_id,customer_id,total_amount_cents,due_date)"
                  + " VALUES (?,?,?,?,?)",
              id,
              business,
              input.customerId(),
              total,
              input.dueDate());
          int position = 0;
          for (var item : input.items())
            jdbc.update(
                "INSERT INTO"
                    + " billing.invoice_items(id,invoice_id,position,description,quantity,unit_amount_cents)"
                    + " VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                position++,
                item.description().strip(),
                item.quantity(),
                item.unitAmountCents());
          events.record(
              business,
              "invoice.created",
              id,
              Map.of(
                  "invoice_id",
                  id,
                  "state",
                  "open",
                  "total_amount_cents",
                  total,
                  "currency",
                  "USD"));
          return get(business, id);
        });
  }

  public Map<String, Object> get(UUID business, UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT id,customer_id,state,currency,total_amount_cents,due_date,created_at,paid_at"
                + " FROM billing.invoices WHERE business_id=? AND id=?",
            business,
            id);
    if (rows.isEmpty()) throw ApiError.missing();
    var invoice = Rows.clean(rows.get(0));
    invoice.put(
        "items",
        jdbc
            .queryForList(
                "SELECT description,quantity,unit_amount_cents FROM billing.invoice_items WHERE"
                    + " invoice_id=? ORDER BY position",
                id)
            .stream()
            .map(Rows::clean)
            .toList());
    String reason =
        blockReason(
            invoice,
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "SELECT unresolved FROM billing.invoice_payment_availability WHERE"
                        + " invoice_id=?",
                    Boolean.class,
                    id)));
    invoice.put("allowed_actions", reason == null ? List.of("pay") : List.of());
    invoice.put("payment_block_reason", reason);
    return invoice;
  }

  public Map<String, Object> lock(UUID business, UUID invoice) {
    var rows =
        jdbc.queryForList(
            "SELECT id,state,total_amount_cents FROM billing.invoices WHERE business_id=? AND id=?"
                + " FOR UPDATE",
            business,
            invoice);
    if (rows.isEmpty()) throw ApiError.missing();
    return rows.get(0);
  }

  private String blockReason(Map<String, Object> invoice, boolean unresolved) {
    return "paid".equals(invoice.get("state"))
        ? "invoice_already_paid"
        : unresolved ? "payment_in_progress" : null;
  }

  public void requirePayable(Map<String, Object> invoice, boolean unresolved) {
    String reason = blockReason(invoice, unresolved);
    if (reason != null)
      throw ApiError.conflict(
          reason,
          reason.equals("invoice_already_paid")
              ? "Invoice is already paid."
              : "An existing payment is still unresolved.");
  }

  public void requireExists(UUID business, UUID invoice) {
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM billing.invoices WHERE business_id=? AND id=?)",
            Boolean.class,
            business,
            invoice))) throw ApiError.missing();
  }

  public void markPaid(UUID business, UUID invoice) {
    if (jdbc.update(
            "UPDATE billing.invoices SET state='paid',paid_at=now() WHERE business_id=? AND id=?"
                + " AND state='open'",
            business,
            invoice)
        != 1) throw ApiError.conflict("invalid_transition", "Invoice cannot transition to paid.");
  }
}
