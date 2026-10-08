package dev.dodo.notifications;

import dev.dodo.common.ApiError;
import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import dev.dodo.common.Pages;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookService {
	private static final QWebhookEndpointEntity ENDPOINT = QWebhookEndpointEntity.webhookEndpointEntity;
	private static final QWebhookDeliveryEntity DELIVERY = QWebhookDeliveryEntity.webhookDeliveryEntity;
	private static final QEventEntity EVENT = QEventEntity.eventEntity;
	private final WebhookEndpointRepository endpoints;
	private final WebhookDeliveryRepository deliveries;
	private final EventRepository events;
	private final WebhookEvents envelopes;
	private final SecretCipher cipher;
	private final WebhookUrlPolicy policy;
	private final Pages pages;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RegisteredWebhookEndpoint register(UUID business, String url) {
		if (policy.check(url.strip()) != WebhookUrlPolicy.Verdict.ALLOWED) throw ApiError.invalid(Messages.WEBHOOK_ADDRESS_NOT_ALLOWED);
		String address = WebhookUrlPolicy.normalize(url.strip());
		if (endpoints.existsByBusinessIdAndUrlAndActiveTrue(business, address)) throw ApiError.conflict("webhook_endpoint_exists", Messages.WEBHOOK_ADDRESS_EXISTS);
		byte[] secret = cipher.generate();
		try {
			var endpoint = endpoints.saveAndFlush(WebhookEndpointEntity.builder().businessId(business).url(address).secretCiphertext(cipher.encrypt(secret)).build());
			return new RegisteredWebhookEndpoint(endpoint.getId(), address, Base64.getEncoder().encodeToString(secret), endpoint.isActive());
		} catch (DataIntegrityViolationException e) {
			throw ApiError.conflict("webhook_endpoint_exists", Messages.WEBHOOK_ADDRESS_EXISTS);
		}
	}

	@Transactional
	public WebhookEndpoint deactivate(UUID business, UUID id) {
		var endpoint = endpoints.findByBusinessIdAndId(business, id).orElseThrow(ApiError::missing);
		endpoint.deactivate();
		return WebhookEndpoint.from(endpoint);
	}

	public WebhookEndpoint endpoint(UUID business, UUID id) {
		return endpoints.findByBusinessIdAndId(business, id).map(WebhookEndpoint::from).orElseThrow(ApiError::missing);
	}

	public Page<WebhookEndpoint> endpoints(UUID business, PageQuery page) {
		return pages.list(endpoints, ENDPOINT.businessId.eq(business), business, "endpoints", page, WebhookEndpoint::from);
	}

	public Page<WebhookDelivery> deliveries(UUID business, UUID invoice, PageQuery page) {
		var where = DELIVERY.businessId.eq(business).and(Optional.ofNullable(invoice).map(DELIVERY.invoiceId::eq).orElse(null));
		return pages.list(deliveries, where, business, "deliveries:" + invoice, page, WebhookDelivery::from);
	}

	public Page<Event> events(UUID business, PageQuery page) {
		return pages.list(events, EVENT.businessId.eq(business), business, "events", page, event -> new Event(event.getId(), event.getEventType(), envelopes.envelope(event), event.getCreatedAt()));
	}
}
