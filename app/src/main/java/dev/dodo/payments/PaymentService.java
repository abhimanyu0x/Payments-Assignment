package dev.dodo.payments;

import dev.dodo.billing.InvoiceEventData;
import dev.dodo.billing.InvoicePayments;
import dev.dodo.common.ApiError;
import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import dev.dodo.common.Pages;
import dev.dodo.configuration.AppProperties;
import dev.dodo.notifications.EventType;
import dev.dodo.notifications.WebhookEvents;
import dev.dodo.platform.Sha256;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
	public static final String IDEMPOTENCY_KEY_PATTERN = "^[!-~]{1,128}$";
	private static final QPaymentAttemptEntity ATTEMPT = QPaymentAttemptEntity.paymentAttemptEntity;

	private final InvoicePayments invoices;
	private final PaymentAttemptRepository attempts;
	private final TransactionTemplate tx;
	private final WebhookEvents events;
	private final AppProperties app;
	private final Pages pages;
	private final Clock clock;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public PaymentAccepted accept(UUID business, UUID invoice, String key, String token) {
		if (!StringUtils.hasText(key) || !key.matches(IDEMPOTENCY_KEY_PATTERN))
			throw new ApiError(400, "idempotency_key_required", Messages.PAYMENT_REFERENCE_REQUIRED);
		String fingerprint = Sha256.hex("pay|" + invoice + "|" + token);
		try {
			return tx.execute(s -> {
				var payable = invoices.lock(business, invoice);
				var previous = attempts.findByBusinessIdAndIdempotencyKey(business, key);
				if (previous.isPresent()) return replay(previous.get(), fingerprint);
				invoices.requirePayable(payable, attempts.unresolved(invoice));
				var now = Instant.now(clock);
				var attempt = attempts.save(PaymentAttemptEntity.builder()
					.businessId(business)
					.invoiceId(invoice)
					.idempotencyKey(key)
					.requestFingerprint(fingerprint)
					.mockCardToken(token)
					.amountCents(payable.totalAmountCents())
					.nextAttemptAt(now)
					.recoveryDeadlineAt(now.plus(app.paymentRecovery()))
					.build());
				return PaymentAccepted.of(attempt);
			});
		} catch (DataIntegrityViolationException e) {
			return attempts.findByBusinessIdAndIdempotencyKey(business, key)
				.map(previous -> replay(previous, fingerprint))
				.orElseThrow(() -> ApiError.conflict("payment_in_progress", Messages.PAYMENT_IN_PROGRESS));
		}
	}

	private static PaymentAccepted replay(PaymentAttemptEntity previous, String fingerprint) {
		if (!fingerprint.equals(previous.getRequestFingerprint()))
			throw ApiError.conflict("idempotency_key_conflict", Messages.REQUEST_REUSED);
		return PaymentAccepted.of(previous);
	}

	public PaymentAttempt get(UUID business, UUID id) {
		return attempts.findByBusinessIdAndId(business, id).map(PaymentAttempt::from).orElseThrow(ApiError::missing);
	}

	public Page<PaymentAttempt> history(UUID business, UUID invoice, PageQuery page) {
		invoices.requireExists(business, invoice);
		return pages.list(attempts, ATTEMPT.businessId.eq(business).and(ATTEMPT.invoiceId.eq(invoice)), business, "attempts:" + invoice, page, PaymentAttempt::from);
	}

	@Transactional
	public void finish(PaymentAttemptEntity claim, PaymentProcessorClient.Result result) {
		UUID business = claim.getBusinessId(), invoice = claim.getInvoiceId();
		invoices.lock(business, invoice);
		var found = attempts.findForUpdateByBusinessIdAndId(business, claim.getId()).filter(attempt -> attempt.getClaimVersion() == claim.getClaimVersion() && attempt.unresolved());
		if (found.isEmpty()) return;
		var current = found.get();
		var now = Instant.now(clock);
		switch (result.status()) {
			case SUCCEEDED -> {
				current.complete(PaymentStatus.SUCCEEDED, result.reference(), null, now);
				invoices.markPaid(business, invoice);
				events.record(business, EventType.INVOICE_PAID, invoice, invoice, InvoiceEventData.paid(invoice, current.getId(), current.getAmountCents()));
			}
			case FAILED -> {
				current.complete(PaymentStatus.FAILED, null, result.failure(), now);
				events.record(business, EventType.INVOICE_PAYMENT_FAILED, current.getId(), invoice, InvoiceEventData.failed(invoice, current.getId(), current.getAmountCents(), result.failure()));
			}
			default -> {
				int round = current.getReconciliationRoundCount();
				boolean exhausted = current.isReviewRequired() || round >= app.paymentRetryDelays().size() || current.getRecoveryDeadlineAt().isBefore(now);
				String error = Objects.requireNonNullElse(result.failure(), "confirmation_pending");
				current.defer(error, now.plus(exhausted ? app.paymentReviewInterval() : app.paymentRetryDelay(round)), exhausted);
			}
		}
	}
}
