package dev.dodo.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(schema = "notifications", name = "webhook_deliveries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WebhookDeliveryEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "business_id", nullable = false)
	private UUID businessId;
	@Column(name = "event_id", nullable = false)
	private UUID eventId;
	@Column(name = "endpoint_id", nullable = false)
	private UUID endpointId;
	@Column(name = "event_type", nullable = false, columnDefinition = "text")
	private EventType eventType;
	@Column(name = "invoice_id", nullable = false)
	private UUID invoiceId;
	@Builder.Default
	@Column(nullable = false, columnDefinition = "text")
	private DeliveryStatus status = DeliveryStatus.PENDING;
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;
	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;
	@Column(name = "lease_expires_at")
	private Instant leaseExpiresAt;
	@Column(name = "claim_version", nullable = false)
	private long claimVersion;
	@Column(name = "last_http_status")
	private Integer lastHttpStatus;
	@Column(name = "last_error_code", columnDefinition = "text")
	private String lastErrorCode;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "delivered_at")
	private Instant deliveredAt;
	@Column(name = "delivery_deadline_at", nullable = false)
	private Instant deliveryDeadlineAt;

	static WebhookDeliveryEntity pending(EventEntity event, UUID endpoint, Instant now, Duration budget) {
		return builder()
			.businessId(event.getBusinessId())
			.eventId(event.getId())
			.endpointId(endpoint)
			.eventType(event.getEventType())
			.invoiceId(event.getInvoiceId())
			.nextAttemptAt(now)
			.deliveryDeadlineAt(now.plus(budget))
			.build();
	}

	void claim(Instant leaseUntil) {
		attemptCount++;
		claimVersion++;
		leaseExpiresAt = leaseUntil;
	}
}
