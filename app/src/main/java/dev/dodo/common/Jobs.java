package dev.dodo.common;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Jobs {
	private final ObservationRegistry observations;

	public <T> void drain(String name, ThreadPoolTaskExecutor executor, Supplier<Optional<T>> claim, Function<T, UUID> id, Consumer<T> work) {
		int free = executor.getMaxPoolSize() - executor.getActiveCount();
		for (int i = 0; i < free; i++) {
			Optional<T> job;
			try {
				job = claim.get();
			} catch (RuntimeException e) {
				log.warn("job_claim_failed job={} type={}", name, e.getClass().getSimpleName(), e);
				return;
			}
			if (job.isEmpty()) return;
			try {
				executor.execute(() -> run(name, id.apply(job.get()), () -> work.accept(job.get())));
			} catch (TaskRejectedException e) {
				log.warn("job_rejected job={} id={}", name, id.apply(job.get()));
				return;
			}
		}
	}

	private void run(String name, UUID id, Runnable work) {
		try {
			Observation.createNotStarted(name, observations).highCardinalityKeyValue("id", id.toString()).observe(() -> {
				work.run();
				log.info("job_done job={} id={}", name, id);
			});
		} catch (RuntimeException e) {
			log.warn("job_incomplete job={} id={} type={}", name, id, e.getClass().getSimpleName(), e);
		}
	}
}
