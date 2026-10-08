package dev.dodo.notifications;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.dodo.configuration.AppProperties;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.LockOptions;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class WebhookDeliveryClaimsImpl implements WebhookDeliveryClaims {
	private static final QWebhookDeliveryEntity DELIVERY = QWebhookDeliveryEntity.webhookDeliveryEntity;
	private final JPAQueryFactory query;
	private final AppProperties app;
	private final Clock clock;

	@Override
	@Transactional
	public void exhaustOverdue() {
		var now = Instant.now(clock);
		query.update(DELIVERY)
			.set(DELIVERY.status, DeliveryStatus.EXHAUSTED)
			.setNull(DELIVERY.leaseExpiresAt)
			.where(DELIVERY.status.eq(DeliveryStatus.PENDING),
				DELIVERY.attemptCount.goe(app.webhookMaxAttempts()).or(DELIVERY.deliveryDeadlineAt.loe(now)),
				DELIVERY.leaseExpiresAt.isNull().or(DELIVERY.leaseExpiresAt.loe(now)))
			.execute();
	}

	@Override
	@Transactional
	public Optional<WebhookDeliveryEntity> claim() {
		var now = Instant.now(clock);
		var due = query.selectFrom(DELIVERY)
			.where(DELIVERY.status.eq(DeliveryStatus.PENDING),
				DELIVERY.attemptCount.lt(app.webhookMaxAttempts()),
				DELIVERY.deliveryDeadlineAt.gt(now),
				DELIVERY.nextAttemptAt.loe(now),
				DELIVERY.leaseExpiresAt.isNull().or(DELIVERY.leaseExpiresAt.loe(now)))
			.orderBy(DELIVERY.nextAttemptAt.asc(), DELIVERY.id.asc())
			.limit(1)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.setHint("jakarta.persistence.lock.timeout", LockOptions.SKIP_LOCKED)
			.fetchFirst();
		return Optional.ofNullable(due).map(delivery -> {
			delivery.claim(now.plus(app.webhookLease()));
			return delivery;
		});
	}

	@Override
	@Transactional
	public void recordResult(WebhookDeliveryEntity job, DeliveryStatus next, Integer httpStatus, String error, Instant retryAt) {
		var now = Instant.now(clock);
		var update = query.update(DELIVERY)
			.set(DELIVERY.status, next)
			.set(DELIVERY.lastHttpStatus, httpStatus)
			.set(DELIVERY.lastErrorCode, error)
			.set(DELIVERY.deliveredAt, next == DeliveryStatus.DELIVERED ? now : null)
			.setNull(DELIVERY.leaseExpiresAt);
		if (next == DeliveryStatus.PENDING) update.set(DELIVERY.nextAttemptAt, retryAt);
		update.where(DELIVERY.id.eq(job.getId()), DELIVERY.claimVersion.eq(job.getClaimVersion()), DELIVERY.status.eq(DeliveryStatus.PENDING)).execute();
	}
}
