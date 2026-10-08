package dev.dodo.mock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class MockPayments {
	public record Payment(
		@NotNull UUID operation_id,
		@NotNull @Min(1) @Max(1000000000000L) Long amount_cents,
		@NotNull @Pattern(regexp = "USD") String currency,
		@NotNull @Pattern(regexp = "tok_(success|insufficient_funds|card_declined|timeout|network_error)") String card_token) {
	}

	private final MockProcessor processor;
	private final Clock clock;

	@PostMapping("/payments")
	public ResponseEntity<Map<String, Object>> pay(@Valid @RequestBody Payment input) throws InterruptedException {
		var operation = processor.register(input);
		if (!Objects.equals(MockProcessor.fingerprint(input), operation.getRequestFingerprint())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Operation parameters changed");
		if (MockProcessor.NETWORK_ERROR.equals(input.card_token())) return ResponseEntity.internalServerError().body(Map.of("error", "processor_unavailable"));
		long wait = Duration.between(Instant.now(clock), operation.getCompleteAfter()).toMillis();
		if (wait > 0) Thread.sleep(wait + 1);
		return ResponseEntity.ok(get(input.operation_id()));
	}

	@GetMapping("/payments/{id}")
	public Map<String, Object> get(@PathVariable UUID id) {
		processor.completeDue();
		var operation = processor.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		var out = new LinkedHashMap<String, Object>();
		out.put("status", operation.getStatus());
		if (Objects.nonNull(operation.getPspRef())) out.put("psp_ref", operation.getPspRef());
		if (Objects.nonNull(operation.getFailureCode())) out.put("code", operation.getFailureCode());
		out.put("post_count", operation.getPostCount());
		return out;
	}
}
