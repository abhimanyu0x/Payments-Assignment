package dev.dodo.customers;

import dev.dodo.http.Pages;
import dev.dodo.identity.Business;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  public record Create(
      @NotBlank @Size(max = 200) String name, @NotBlank @Email @Size(max = 254) String email) {}

  private final CustomerService service;
  private final Pages pages;

  public CustomerController(CustomerService service, Pages pages) {
    this.service = service;
    this.pages = pages;
  }

  @PostMapping
  public ResponseEntity<?> create(HttpServletRequest r, @Valid @RequestBody Create body) {
    var customer = service.create(Business.from(r), body.name(), body.email());
    return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.get("id")))
        .body(customer);
  }

  @GetMapping("/{id}")
  public Object get(HttpServletRequest r, @PathVariable UUID id) {
    return service.get(Business.from(r), id);
  }

  @GetMapping
  public Object list(
      HttpServletRequest r,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return pages.list(
        "customers.customers",
        "id,name,email,created_at",
        Business.from(r),
        "customers",
        "",
        List.of(),
        limit,
        cursor);
  }
}
