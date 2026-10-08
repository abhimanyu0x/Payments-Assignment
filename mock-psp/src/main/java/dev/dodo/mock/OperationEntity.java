package dev.dodo.mock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(schema = "mock_psp", name = "operations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OperationEntity implements Persistable<UUID> {
	@Id
	@Column(name = "operation_id")
	private UUID operationId;
	@Column(name = "request_fingerprint", nullable = false, columnDefinition = "text")
	private String requestFingerprint;
	@Column(name = "amount_cents", nullable = false)
	private long amountCents;
	@Column(nullable = false, columnDefinition = "text")
	private String currency;
	@Column(name = "card_token", nullable = false, columnDefinition = "text")
	private String cardToken;
	@Builder.Default
	@Column(nullable = false, columnDefinition = "text")
	private OperationStatus status = OperationStatus.PENDING;
	@Column(name = "psp_ref")
	private UUID pspRef;
	@Column(name = "failure_code", columnDefinition = "text")
	private String failureCode;
	@Column(name = "complete_after", nullable = false)
	private Instant completeAfter;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "completed_at")
	private Instant completedAt;
	@Builder.Default
	@Column(name = "post_count", nullable = false)
	private int postCount = 1;
	@Transient
	@Builder.Default
	private boolean fresh = true;

	@Override
	public UUID getId() {
		return operationId;
	}

	@Override
	public boolean isNew() {
		return fresh;
	}

	@PostLoad
	@PostPersist
	void stored() {
		fresh = false;
	}

	void failWithProcessorError(Instant now) {
		status = OperationStatus.FAILED;
		failureCode = "processor_error";
		completedAt = now;
	}

	void complete(Instant now) {
		switch (cardToken) {
			case "tok_card_declined" -> fail("card_declined");
			case "tok_insufficient_funds" -> fail("insufficient_funds");
			default -> {
				status = OperationStatus.SUCCEEDED;
				pspRef = operationId;
			}
		}
		completedAt = now;
	}

	private void fail(String code) {
		status = OperationStatus.FAILED;
		failureCode = code;
	}

	void countRepeat() {
		postCount++;
	}
}
