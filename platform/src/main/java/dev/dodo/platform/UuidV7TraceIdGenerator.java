package dev.dodo.platform;

import io.opentelemetry.sdk.trace.IdGenerator;

public class UuidV7TraceIdGenerator implements IdGenerator {
	private final IdGenerator random = IdGenerator.random();

	@Override
	public String generateSpanId() {
		return random.generateSpanId();
	}

	@Override
	public String generateTraceId() {
		return UuidV7.hex();
	}
}
