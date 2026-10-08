package dev.dodo.payments;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptClaims {
	Optional<PaymentAttemptEntity> claim();

	void countCall(UUID id, long claimVersion);
}
