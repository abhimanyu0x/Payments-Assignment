package dev.dodo.platform;

import io.micrometer.tracing.Tracer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;

@RequiredArgsConstructor
public class TraceIds {
	private final ObjectProvider<Tracer> tracer;

	public String current() {
		return Optional.ofNullable(tracer.getIfAvailable()).map(Tracer::currentSpan).map(span -> span.context().traceId()).orElse(null);
	}
}
