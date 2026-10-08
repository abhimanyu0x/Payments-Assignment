package dev.dodo.notifications;

import dev.dodo.common.Jobs;
import dev.dodo.configuration.AppProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.workers-enabled", havingValue = "true")
public class WebhookWorker {
	private final WebhookDeliveryRepository deliveries;
	private final EventRepository events;
	private final WebhookEndpointRepository endpoints;
	private final WebhookEvents envelopes;
	private final SecretCipher cipher;
	private final WebhookSigner signer;
	private final AppProperties app;
	private final WebhookUrlPolicy policy;
	private final Clock clock;
	private final Jobs jobs;
	@Qualifier("webhookClient")
	private final RestClient http;
	@Qualifier("webhookExecutor")
	private final ThreadPoolTaskExecutor executor;

	@Scheduled(fixedDelayString = "${app.worker-poll-interval}")
	public void poll() {
		try {
			deliveries.exhaustOverdue();
		} catch (RuntimeException e) {
			log.warn("webhook_sweep_failed type={}", e.getClass().getSimpleName(), e);
			return;
		}
		jobs.drain("webhook.delivery", executor, deliveries::claim, WebhookDeliveryEntity::getId, this::deliver);
	}

	private void deliver(WebhookDeliveryEntity job) {
		var endpoint = endpoints.findById(job.getEndpointId()).orElseThrow();
		if (!endpoint.isActive()) {
			deliveries.recordResult(job, DeliveryStatus.EXHAUSTED, null, "endpoint_disabled", null);
			return;
		}
		var verdict = policy.check(endpoint.getUrl());
		if (verdict == WebhookUrlPolicy.Verdict.BLOCKED) {
			log.warn("webhook_blocked delivery_id={} endpoint_id={}", job.getId(), endpoint.getId());
			deliveries.recordResult(job, DeliveryStatus.EXHAUSTED, null, "endpoint_not_allowed", null);
			return;
		}
		Integer status = null;
		String error = "dns_error";
		if (verdict == WebhookUrlPolicy.Verdict.ALLOWED) {
			try {
				status = send(job, endpoint);
				error = status >= 200 && status < 300 ? null : "http_" + status;
			} catch (RuntimeException e) {
				log.warn("webhook_attempt_failed delivery_id={} type={}", job.getId(), e.getClass().getSimpleName(), e);
				error = "delivery_error";
			}
		}
		boolean success = Objects.isNull(error);
		int attempt = job.getAttemptCount();
		var next = success ? DeliveryStatus.DELIVERED : attempt >= app.webhookMaxAttempts() ? DeliveryStatus.EXHAUSTED : DeliveryStatus.PENDING;
		deliveries.recordResult(job, next, status, error, Instant.now(clock).plus(app.webhookRetryDelay(attempt)));
	}

	private int send(WebhookDeliveryEntity job, WebhookEndpointEntity endpoint) {
		Assert.state(job.getDeliveryDeadlineAt().isAfter(Instant.now(clock)), "Delivery deadline passed");
		var event = events.findById(job.getEventId()).orElseThrow();
		String body = envelopes.envelope(event);
		long timestamp = Instant.now(clock).getEpochSecond();
		String signature = signer.sign(cipher.decrypt(endpoint.getSecretCiphertext()), timestamp, body);
		return http.post()
			.uri(URI.create(endpoint.getUrl()))
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-Webhook-Id", event.getId().toString())
			.header("X-Webhook-Timestamp", Long.toString(timestamp))
			.header("X-Webhook-Signature", "v1=" + signature)
			.body(body.getBytes(StandardCharsets.UTF_8))
			.exchange((request, response) -> response.getStatusCode().value());
	}
}
