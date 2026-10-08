package dev.dodo.payments;

import java.net.URI;
import java.util.UUID;

public record PaymentAccepted(UUID paymentAttemptId, UUID invoiceId, PaymentStatus status, String statusUrl) {
	static PaymentAccepted of(PaymentAttemptEntity attempt) {
		return new PaymentAccepted(attempt.getId(), attempt.getInvoiceId(), PaymentStatus.PENDING, "/api/v1/payment-attempts/" + attempt.getId());
	}

	URI location() {
		return URI.create(statusUrl);
	}
}
