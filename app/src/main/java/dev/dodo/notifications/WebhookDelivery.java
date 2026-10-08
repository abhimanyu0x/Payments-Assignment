package dev.dodo.notifications;

import java.time.Instant;
import java.util.UUID;

public record WebhookDelivery(
	UUID id,
	UUID eventId,
	UUID endpointId,
	EventType eventType,
	UUID invoiceId,
	DeliveryStatus status,
	int attemptCount,
	Instant nextAttemptAt,
	Integer lastHttpStatus,
	String lastErrorCode,
	Instant createdAt,
	Instant deliveredAt) {
	static WebhookDelivery from(WebhookDeliveryEntity delivery) {
		return new WebhookDelivery(
			delivery.getId(),
			delivery.getEventId(),
			delivery.getEndpointId(),
			delivery.getEventType(),
			delivery.getInvoiceId(),
			delivery.getStatus(),
			delivery.getAttemptCount(),
			delivery.getNextAttemptAt(),
			delivery.getLastHttpStatus(),
			delivery.getLastErrorCode(),
			delivery.getCreatedAt(),
			delivery.getDeliveredAt());
	}
}
