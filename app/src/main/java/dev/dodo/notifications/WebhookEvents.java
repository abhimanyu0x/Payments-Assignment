package dev.dodo.notifications;

import dev.dodo.common.Json;
import dev.dodo.configuration.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class WebhookEvents {
	private final EventRepository events;
	private final WebhookEndpointRepository endpoints;
	private final WebhookDeliveryRepository deliveries;
	private final AppProperties app;
	private final Json json;
	private final Clock clock;

	public void record(UUID business, EventType type, UUID source, UUID invoice, Object data) {
		Assert.state(TransactionSynchronizationManager.isActualTransactionActive(), "An event must join a business transaction.");
		if (events.existsByBusinessIdAndEventTypeAndSourceId(business, type, source)) return;
		var event = events.save(EventEntity.builder().businessId(business).eventType(type).sourceId(source).invoiceId(invoice).data(json.write(data)).build());
		var now = Instant.now(clock);
		deliveries.saveAll(endpoints.findByBusinessIdAndActiveTrue(business).stream()
			.map(endpoint -> WebhookDeliveryEntity.pending(event, endpoint.getId(), now, app.webhookDeliveryBudget()))
			.toList());
	}

	public String envelope(EventEntity event) {
		return json.write(WebhookEnvelope.of(event));
	}
}
