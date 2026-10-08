package dev.dodo.mock;

import dev.dodo.platform.Sha256;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MockProcessor {
	static final String NETWORK_ERROR = "tok_network_error";
	static final String TIMEOUT = "tok_timeout";
	private static final Duration SLOW = Duration.ofSeconds(30);
	private static final Duration FAST = Duration.ofMillis(100);

	private final OperationRepository operations;
	private final Clock clock;

	@Transactional
	public OperationEntity register(MockPayments.Payment input) {
		var existing = operations.findForUpdateByOperationId(input.operation_id());
		if (existing.isPresent()) {
			existing.get().countRepeat();
			return existing.get();
		}
		var now = Instant.now(clock);
		var operation = OperationEntity.builder()
			.operationId(input.operation_id())
			.requestFingerprint(fingerprint(input))
			.amountCents(input.amount_cents())
			.currency(input.currency())
			.cardToken(input.card_token())
			.completeAfter(now.plus(TIMEOUT.equals(input.card_token()) ? SLOW : FAST))
			.build();
		if (NETWORK_ERROR.equals(input.card_token())) operation.failWithProcessorError(now);
		return operations.saveAndFlush(operation);
	}

	@Transactional(readOnly = true)
	public Optional<OperationEntity> find(UUID id) {
		return operations.findById(id);
	}

	@Scheduled(fixedDelay = 250)
	@Transactional
	public void completeDue() {
		var now = Instant.now(clock);
		operations.findByStatusAndCompleteAfterLessThanEqual(OperationStatus.PENDING, now).forEach(operation -> operation.complete(now));
	}

	static String fingerprint(MockPayments.Payment input) {
		return Sha256.hex(input.amount_cents() + "|" + input.currency() + "|" + input.card_token());
	}
}
