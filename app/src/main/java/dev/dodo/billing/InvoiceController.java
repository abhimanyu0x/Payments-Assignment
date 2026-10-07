package dev.dodo.billing;

import dev.dodo.http.*;
import dev.dodo.identity.Business;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
  private final InvoiceService service;
  private final Pages pages;

  public InvoiceController(InvoiceService service, Pages pages) {
    this.service = service;
    this.pages = pages;
  }

  @PostMapping
  public ResponseEntity<?> create(HttpServletRequest r, @Valid @RequestBody InvoiceInput input) {
    var invoice = service.create(Business.from(r), input);
    return ResponseEntity.created(URI.create("/api/v1/invoices/" + invoice.get("id")))
        .body(invoice);
  }

  @PostMapping("/preview")
  public Object preview(HttpServletRequest r, @Valid @RequestBody InvoiceInput input) {
    return service.preview(Business.from(r), input);
  }

  @GetMapping("/{id}")
  public Object get(HttpServletRequest r, @PathVariable UUID id) {
    return service.get(Business.from(r), id);
  }

  @GetMapping
  public Object list(
      HttpServletRequest r,
      @RequestParam(required = false) String state,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    if (state != null && !List.of("open", "paid").contains(state))
      throw ApiError.invalid("state must be open or paid.");
    return pages.list(
        "billing.invoices",
        "id,customer_id,state,currency,total_amount_cents,due_date,created_at,paid_at",
        Business.from(r),
        "invoices:" + state,
        state == null ? "" : " AND state=?",
        state == null ? List.of() : List.of(state),
        limit,
        cursor);
  }
}
