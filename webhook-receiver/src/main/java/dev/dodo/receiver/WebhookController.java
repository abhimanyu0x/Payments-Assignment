package dev.dodo.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {
	private final SignatureVerifier verifier;
	private final ReceivedEvents events;
	private final ObjectMapper mapper;
	private final ReceiverProperties properties;

	@PostMapping("/webhooks")
	public ResponseEntity<Map<String, Object>> receive(
		@RequestHeader(value = "X-Webhook-Id", required = false) String id,
		@RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
		@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
		HttpServletRequest request) throws IOException {
		long limit = properties.maxBodySize().toBytes();
		if (request.getContentLengthLong() > limit) return tooLarge();
		byte[] body = request.getInputStream().readNBytes(Math.toIntExact(limit + 1));
		if (body.length > limit) return tooLarge();
		if (!verifier.verify(body, timestamp, signature)) return invalid();
		JsonNode event;
		UUID eventId;
		try {
			event = mapper.readTree(body);
			eventId = UUID.fromString(event.path("id").asText());
		} catch (Exception e) {
			return invalid();
		}
		String type = event.path("type").asText();
		if (!Objects.equals(eventId.toString(), id) || !StringUtils.hasText(type)) return invalid();
		var received = ReceivedEventEntity.builder().eventId(eventId).eventType(type).payload(new String(body, StandardCharsets.UTF_8)).build();
		boolean duplicate;
		try {
			duplicate = !events.saveIfNew(received);
		} catch (DataIntegrityViolationException e) {
			duplicate = true;
		}
		log.info("webhook_received event_id={} type={} duplicate={}", eventId, type, duplicate);
		return ResponseEntity.ok(Map.of("received", true, "duplicate", duplicate));
	}

	@GetMapping("/events")
	public Map<String, List<JsonNode>> events() {
		return Map.of("data", events.recentPayloads().stream().map(this::parse).toList());
	}

	private JsonNode parse(String payload) {
		try {
			return mapper.readTree(payload);
		} catch (Exception e) {
			throw new IllegalStateException("Stored payload is not JSON", e);
		}
	}

	private static ResponseEntity<Map<String, Object>> tooLarge() {
		return ResponseEntity.status(413).body(Map.of("error", "payload_too_large"));
	}

	private static ResponseEntity<Map<String, Object>> invalid() {
		return ResponseEntity.badRequest().body(Map.of("error", "invalid_webhook"));
	}
}
