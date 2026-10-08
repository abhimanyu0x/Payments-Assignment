package dev.dodo.platform;

import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.tracing.SdkTracerProviderBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.DateTimeProvider;

@AutoConfiguration(afterName = {"org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration", "org.springframework.boot.actuate.autoconfigure.tracing.NoopTracerAutoConfiguration"})
public class PlatformAutoConfiguration {
	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));
	}

	@Bean
	SdkTracerProviderBuilderCustomizer uuidV7TraceIds() {
		return builder -> builder.setIdGenerator(new UuidV7TraceIdGenerator());
	}

	@Bean
	TraceIds traceIds(ObjectProvider<Tracer> tracer) {
		return new TraceIds(tracer);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(DateTimeProvider.class)
	static class Auditing {
		@Bean
		DateTimeProvider auditingDateTimeProvider(Clock clock) {
			return () -> Optional.of(Instant.now(clock));
		}
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	static class Web {
		@Bean
		FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter(TraceIds traceIds) {
			var registration = new FilterRegistrationBean<>(new TraceIdResponseFilter(traceIds));
			registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
			return registration;
		}
	}
}
