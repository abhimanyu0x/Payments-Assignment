package dev.dodo.notifications;

import java.time.Instant;
import java.util.UUID;

public record WebhookEndpoint(UUID id, String url, boolean active, Instant createdAt) {
	static WebhookEndpoint from(WebhookEndpointEntity endpoint) {
		return new WebhookEndpoint(endpoint.getId(), endpoint.getUrl(), endpoint.isActive(), endpoint.getCreatedAt());
	}
}
