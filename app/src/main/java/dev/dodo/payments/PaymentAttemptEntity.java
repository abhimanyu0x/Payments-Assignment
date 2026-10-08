package dev.dodo.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(schema = "payments", name = "payment_attempts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentAttemptEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "business_id", nullable = false)
	private UUID businessId;
	@Column(name = "invoice_id", nullable = false)
	private UUID invoiceId;
	@Column(name = "idempotency_key", nullable = false, columnDefinition = "text")
	private String idempotencyKey;
	@Column(name = "request_fingerprint", nullable = false, columnDefinition = "text")
	private String requestFingerprint;
	@Column(name = "mock_card_token", nullable = false, columnDefinition = "text")
	private String mockCardToken;
	@Column(name = "amount_cents", nullable = false)
	private long amountCents;
	@Builder.Default
	@Column(nullable = false, columnDefinition = "text")
	private PaymentStatus status = PaymentStatus.PENDING;
	@Column(name = "psp_reference", columnDefinition = "text")
	private String pspReference;
	@Column(name = "failure_code", columnDefinition = "text")
	private String failureCode;
	@Column(name = "last_error_code", columnDefinition = "text")
	private String lastErrorCode;
	@Column(name = "review_required", nullable = false)
	private boolean reviewRequired;
	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;
	@Column(name = "lease_expires_at")
	private Instant leaseExpiresAt;
	@Column(name = "claim_version", nullable = false)
	private long claimVersion;
	@Column(name = "reconciliation_round_count", nullable = false)
	private int reconciliationRoundCount;
	@Column(name = "processor_call_count", nullable = false)
	private int processorCallCount;
	@Column(name = "recovery_deadline_at", nullable = false)
	private Instant recoveryDeadlineAt;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Column(name = "completed_at")
	private Instant completedAt;

	void claim(Instant leaseUntil) {
		if (claimVersion > 0) reconciliationRoundCount++;
		claimVersion++;
		leaseExpiresAt = leaseUntil;
	}

	void complete(PaymentStatus outcome, String reference, String failure, Instant now) {
		status = outcome;
		pspReference = reference;
		failureCode = failure;
		completedAt = now;
		leaseExpiresAt = null;
		lastErrorCode = null;
	}

	void defer(String error, Instant retryAt, boolean exhausted) {
		status = PaymentStatus.UNKNOWN;
		lastErrorCode = error;
		nextAttemptAt = retryAt;
		leaseExpiresAt = null;
		reviewRequired = exhausted;
	}

	boolean unresolved() {
		return PaymentStatus.UNRESOLVED.contains(status);
	}
}
