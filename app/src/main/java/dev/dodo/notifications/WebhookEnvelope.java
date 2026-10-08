package dev.dodo.notifications;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;
import java.util.UUID;

record WebhookEnvelope(UUID id, EventType type, Instant createdAt, @JsonRawValue String data) {
	static WebhookEnvelope of(EventEntity event) {
		return new WebhookEnvelope(event.getId(), event.getEventType(), event.getCreatedAt(), event.getData());
	}
}
