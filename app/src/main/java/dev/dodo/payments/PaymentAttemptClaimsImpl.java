package dev.dodo.payments;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.dodo.configuration.AppProperties;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.LockOptions;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class PaymentAttemptClaimsImpl implements PaymentAttemptClaims {
	private static final QPaymentAttemptEntity ATTEMPT = QPaymentAttemptEntity.paymentAttemptEntity;
	private final JPAQueryFactory query;
	private final AppProperties app;
	private final Clock clock;

	@Override
	@Transactional
	public Optional<PaymentAttemptEntity> claim() {
		var now = Instant.now(clock);
		var leaseFree = ATTEMPT.leaseExpiresAt.isNull().or(ATTEMPT.leaseExpiresAt.loe(now));
		query.update(ATTEMPT)
			.set(ATTEMPT.status, PaymentStatus.UNKNOWN)
			.set(ATTEMPT.reviewRequired, true)
			.set(ATTEMPT.lastErrorCode, "recovery_budget_exhausted")
			.set(ATTEMPT.updatedAt, now)
			.where(ATTEMPT.status.in(PaymentStatus.UNRESOLVED), ATTEMPT.reviewRequired.isFalse(), ATTEMPT.recoveryDeadlineAt.loe(now), leaseFree)
			.execute();
		var due = query.selectFrom(ATTEMPT)
			.where(ATTEMPT.status.in(PaymentStatus.UNRESOLVED), ATTEMPT.reviewRequired.isFalse(), ATTEMPT.recoveryDeadlineAt.gt(now), ATTEMPT.nextAttemptAt.loe(now), leaseFree)
			.orderBy(ATTEMPT.nextAttemptAt.asc(), ATTEMPT.id.asc())
			.limit(1)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.setHint("jakarta.persistence.lock.timeout", LockOptions.SKIP_LOCKED)
			.fetchFirst();
		return Optional.ofNullable(due).map(attempt -> {
			attempt.claim(now.plus(app.paymentLease()));
			return attempt;
		});
	}

	@Override
	@Transactional
	public void countCall(UUID id, long claimVersion) {
		query.update(ATTEMPT).set(ATTEMPT.processorCallCount, ATTEMPT.processorCallCount.add(1)).where(ATTEMPT.id.eq(id), ATTEMPT.claimVersion.eq(claimVersion)).execute();
	}
}
