package dev.dodo.configuration;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.workers-enabled", havingValue = "true")
public class WorkerConfiguration {
	private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(15);

	@Bean
	ThreadPoolTaskExecutor paymentExecutor(ThreadPoolTaskExecutorBuilder builder, AppProperties app) {
		return executor(builder, app, "payment-");
	}

	@Bean
	ThreadPoolTaskExecutor webhookExecutor(ThreadPoolTaskExecutorBuilder builder, AppProperties app) {
		return executor(builder, app, "webhook-");
	}

	private static ThreadPoolTaskExecutor executor(ThreadPoolTaskExecutorBuilder builder, AppProperties app, String prefix) {
		return builder.corePoolSize(app.workerConcurrency()).maxPoolSize(app.workerConcurrency()).queueCapacity(0)
			.threadNamePrefix(prefix).awaitTermination(true).awaitTerminationPeriod(SHUTDOWN_GRACE).build();
	}
}
