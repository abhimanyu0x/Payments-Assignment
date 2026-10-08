package dev.dodo.notifications;

import java.util.UUID;

public record RegisteredWebhookEndpoint(UUID id, String url, String signingSecret, boolean active) {
}
