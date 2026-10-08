package dev.dodo.notifications;

import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks and events")
@RequestMapping("/api/v1")
public class WebhookController {
	public record NewWebhookEndpoint(@NotBlank(message = Messages.WEBHOOK_ADDRESS_REQUIRED) @Size(max = 2048, message = Messages.WEBHOOK_ADDRESS_TOO_LONG) String url) {
	}

	private final WebhookService service;

	@PostMapping("/webhook-endpoints")
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<RegisteredWebhookEndpoint> registerWebhookEndpoint(@AuthenticationPrincipal UUID business, @Valid @RequestBody NewWebhookEndpoint body) {
		var endpoint = service.register(business, body.url());
		return ResponseEntity.created(URI.create("/api/v1/webhook-endpoints/" + endpoint.id())).body(endpoint);
	}

	@GetMapping("/webhook-endpoints/{id}")
	public WebhookEndpoint getWebhookEndpoint(@AuthenticationPrincipal UUID business, @PathVariable UUID id) {
		return service.endpoint(business, id);
	}

	@GetMapping("/webhook-endpoints")
	public Page<WebhookEndpoint> listWebhookEndpoints(@AuthenticationPrincipal UUID business, @Valid @ParameterObject PageQuery page) {
		return service.endpoints(business, page);
	}

	@GetMapping("/webhook-deliveries")
	public Page<WebhookDelivery> listWebhookDeliveries(@AuthenticationPrincipal UUID business, @RequestParam(name = "invoice_id", required = false) UUID invoiceId, @Valid @ParameterObject PageQuery page) {
		return service.deliveries(business, invoiceId, page);
	}

	@GetMapping("/events")
	public Page<Event> listEvents(@AuthenticationPrincipal UUID business, @Valid @ParameterObject PageQuery page) {
		return service.events(business, page);
	}
}
