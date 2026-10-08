package dev.dodo.notifications;

import java.time.Instant;
import java.util.Optional;

public interface WebhookDeliveryClaims {
	void exhaustOverdue();

	Optional<WebhookDeliveryEntity> claim();

	void recordResult(WebhookDeliveryEntity job, DeliveryStatus next, Integer httpStatus, String error, Instant retryAt);
}
