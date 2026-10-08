package dev.dodo.billing;

import dev.dodo.common.ErrorResponse;
import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import dev.dodo.common.WireValueEditor;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Invoices")
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
	private final InvoiceService service;

	@InitBinder
	void wireValues(WebDataBinder binder) {
		binder.registerCustomEditor(InvoiceState.class, new WireValueEditor<>(InvoiceState.class));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "404", description = Messages.CUSTOMER_NOT_FOUND, content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public ResponseEntity<Invoice> createInvoice(@AuthenticationPrincipal UUID business, @Valid @RequestBody InvoiceInput input) {
		var invoice = service.create(business, input);
		return ResponseEntity.created(URI.create("/api/v1/invoices/" + invoice.id())).body(invoice);
	}

	@PostMapping("/preview")
	@ApiResponse(responseCode = "404", description = Messages.CUSTOMER_NOT_FOUND, content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	public InvoicePreview previewInvoice(@AuthenticationPrincipal UUID business, @Valid @RequestBody InvoiceInput input) {
		return service.preview(business, input);
	}

	@GetMapping("/{id}")
	public Invoice getInvoice(@AuthenticationPrincipal UUID business, @PathVariable UUID id) {
		return service.get(business, id);
	}

	@GetMapping
	public Page<InvoiceSummary> listInvoices(@AuthenticationPrincipal UUID business, @RequestParam(required = false) InvoiceState state, @Valid @ParameterObject PageQuery page) {
		return service.list(business, state, page);
	}
}
