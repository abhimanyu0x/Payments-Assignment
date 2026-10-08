package dev.dodo.customers;

import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Customers")
@RequestMapping("/api/v1/customers")
public class CustomerController {
	public record NewCustomer(
		@NotBlank(message = Messages.NAME_REQUIRED) @Size(max = 200, message = Messages.NAME_TOO_LONG) String name,
		@NotBlank(message = Messages.EMAIL_REQUIRED) @Email(message = Messages.EMAIL_INVALID) @Size(max = 254, message = Messages.EMAIL_TOO_LONG) String email) {
	}

	private final CustomerService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<Customer> createCustomer(@AuthenticationPrincipal UUID business, @Valid @RequestBody NewCustomer body) {
		var customer = service.create(business, body.name(), body.email());
		return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
	}

	@GetMapping("/{id}")
	public Customer getCustomer(@AuthenticationPrincipal UUID business, @PathVariable UUID id) {
		return service.get(business, id);
	}

	@GetMapping
	public Page<Customer> listCustomers(@AuthenticationPrincipal UUID business, @Valid @ParameterObject PageQuery page) {
		return service.list(business, page);
	}
}
