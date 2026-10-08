package dev.dodo.payments;

import dev.dodo.common.Money;
import java.time.Instant;
import java.util.UUID;

public record PaymentAttempt(
	UUID id,
	UUID invoiceId,
	PaymentStatus status,
	long amountCents,
	String amountDisplay,
	String currency,
	String pspReference,
	String failureCode,
	String lastErrorCode,
	boolean reviewRequired,
	Instant createdAt,
	Instant completedAt) {
	static PaymentAttempt from(PaymentAttemptEntity attempt) {
		return new PaymentAttempt(
			attempt.getId(),
			attempt.getInvoiceId(),
			attempt.getStatus(),
			attempt.getAmountCents(),
			Money.display(attempt.getAmountCents()),
			Money.CURRENCY,
			attempt.getPspReference(),
			attempt.getFailureCode(),
			attempt.getLastErrorCode(),
			attempt.isReviewRequired(),
			attempt.getCreatedAt(),
			attempt.getCompletedAt());
	}
}
