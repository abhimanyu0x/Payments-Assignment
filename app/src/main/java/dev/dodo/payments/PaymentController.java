package dev.dodo.payments;

import dev.dodo.common.ErrorResponse;
import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Payments")
@RequestMapping("/api/v1")
public class PaymentController {
	public record PaymentRequest(
		@NotBlank(message = Messages.PAYMENT_METHOD_REQUIRED)
		@Pattern(regexp = "tok_(success|insufficient_funds|card_declined|timeout|network_error)", message = Messages.PAYMENT_METHOD_NOT_SUPPORTED)
		String cardToken) {
	}

	private final PaymentService service;

	@PostMapping("/invoices/{id}/pay")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(
		summary = "Pay an invoice",
		description = Messages.DOC_PAY)
	@ApiResponse(
		responseCode = "409",
		description = Messages.DOC_PAY_CONFLICT,
		content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public ResponseEntity<PaymentAccepted> payInvoice(
		@AuthenticationPrincipal UUID business,
		@PathVariable UUID id,
		@Parameter(
			required = true,
			description = Messages.DOC_PAYMENT_REFERENCE,
			schema = @Schema(pattern = PaymentService.IDEMPOTENCY_KEY_PATTERN))
		@RequestHeader(value = "Idempotency-Key", required = false)
		String key,
		@Valid @RequestBody PaymentRequest body) {
		var accepted = service.accept(business, id, key, body.cardToken());
		return ResponseEntity.accepted().location(accepted.location()).body(accepted);
	}

	@GetMapping("/payment-attempts/{id}")
	public PaymentAttempt getPaymentAttempt(@AuthenticationPrincipal UUID business, @PathVariable UUID id) {
		return service.get(business, id);
	}

	@GetMapping("/invoices/{id}/payment-attempts")
	public Page<PaymentAttempt> listPaymentAttempts(@AuthenticationPrincipal UUID business, @PathVariable UUID id, @Valid @ParameterObject PageQuery page) {
		return service.history(business, id, page);
	}
}
