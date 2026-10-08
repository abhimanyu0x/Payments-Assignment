package dev.dodo.payments;

import dev.dodo.common.Jobs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.workers-enabled", havingValue = "true")
public class PaymentWorker {
	private final PaymentAttemptRepository attempts;
	private final PaymentProcessorClient processor;
	private final PaymentService service;
	private final Jobs jobs;
	@Qualifier("paymentExecutor")
	private final ThreadPoolTaskExecutor executor;

	@Scheduled(fixedDelayString = "${app.worker-poll-interval}")
	public void poll() {
		jobs.drain("payment.attempt", executor, attempts::claim, PaymentAttemptEntity::getId, claim -> service.finish(claim, processor.execute(claim)));
	}
}
