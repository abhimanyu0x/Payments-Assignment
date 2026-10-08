package dev.dodo.payments;

import dev.dodo.billing.PaymentActivity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID>, QuerydslPredicateExecutor<PaymentAttemptEntity>, PaymentAttemptClaims, PaymentActivity {
	Optional<PaymentAttemptEntity> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

	Optional<PaymentAttemptEntity> findByBusinessIdAndId(UUID businessId, UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PaymentAttemptEntity> findForUpdateByBusinessIdAndId(UUID businessId, UUID id);

	boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, Collection<PaymentStatus> statuses);

	@Override
	default boolean unresolved(UUID invoice) {
		return existsByInvoiceIdAndStatusIn(invoice, PaymentStatus.UNRESOLVED);
	}
}
