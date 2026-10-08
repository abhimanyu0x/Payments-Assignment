package dev.dodo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dodo.platform.UuidV7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
class EndToEndTest {
	record Expected(String status, String failureCode, String invoiceState) {}

	record Reply(int status, JsonNode body) {}

	static final String API = setting("API_URL", "http://localhost:8080/api/v1");
	static final String RECEIVER = setting("RECEIVER_URL", "http://localhost:8090");
	static final String KEY = setting("API_KEY", "demo.local-demo-secret-change-for-real-use");
	static final String PSP = setting("PSP_URL", "http://localhost:8081");
	static final ObjectMapper JSON = new ObjectMapper();
	static final Map<String, Expected> TOKENS = new LinkedHashMap<>();

	static {
		TOKENS.put("tok_card_declined", new Expected("failed", "card_declined", "open"));
		TOKENS.put("tok_insufficient_funds", new Expected("failed", "insufficient_funds", "open"));
		TOKENS.put("tok_timeout", new Expected("succeeded", null, "paid"));
		TOKENS.put("tok_network_error", new Expected("failed", "processor_error", "open"));
	}

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	static String setting(String name, String fallback) {
		return Objects.requireNonNullElse(System.getenv(name), fallback);
	}

	Reply call(String path, Object body, String idempotencyKey, String apiKey) throws Exception {
		var request = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
		if (Objects.nonNull(apiKey)) request.header("Authorization", "Bearer " + apiKey);
		if (Objects.nonNull(idempotencyKey)) request.header("Idempotency-Key", idempotencyKey);
		request.method(Objects.isNull(body) ? "GET" : "POST", Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
		var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		return new Reply(response.statusCode(), JSON.readTree(response.body()));
	}

	Reply call(String path) throws Exception {
		return call(path, null, null, KEY);
	}

	Reply pay(String invoice, String token, String idempotencyKey) throws Exception {
		return call("/invoices/" + invoice + "/pay", Map.of("card_token", token), idempotencyKey, KEY);
	}

	String newInvoice() throws Exception {
		var customer = call("/customers", Map.of("name", "End To End", "email", "e2e@example.com"), null, KEY);
		assertEquals(201, customer.status(), customer.body().toString());
		var invoice = call("/invoices", Map.of("customer_id", customer.body().get("id").asText(), "due_date", "2026-12-31", "items", List.of(Map.of("description", "Work", "quantity", 2, "unit_amount_cents", 2500))), null, KEY);
		assertEquals(201, invoice.status(), invoice.body().toString());
		assertEquals(5000, invoice.body().get("total_amount_cents").asLong());
		assertEquals("open", invoice.body().get("state").asText());
		assertEquals(7, UUID.fromString(invoice.body().get("id").asText()).version());
		return invoice.body().get("id").asText();
	}

	JsonNode awaitOutcome(String attempt) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
		while (System.nanoTime() < deadline) {
			var reply = call("/payment-attempts/" + attempt);
			assertEquals(200, reply.status());
			String status = reply.body().get("status").asText();
			if (status.equals("succeeded") || status.equals("failed")) return reply.body();
			Thread.sleep(500);
		}
		throw new AssertionError("Attempt " + attempt + " did not resolve");
	}

	String processorOperations(String attempt) throws Exception {
		var response = client.send(HttpRequest.newBuilder(URI.create(PSP + "/payments/" + UUID.fromString(attempt))).build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) return "0|0";
		assertEquals(200, response.statusCode(), response.body());
		return "1|" + JSON.readTree(response.body()).get("post_count").asInt();
	}

	void awaitApi() throws Exception {
		for (int i = 0; i < 90; i++) {
			try {
				if (call("/customers").status() == 200) return;
			} catch (java.io.IOException e) {
				Thread.sleep(1000);
			}
		}
		throw new AssertionError("API not ready at " + API);
	}

	@Test
	void paymentsReachTheirOutcomesOnceAndWebhooksAreDelivered() throws Exception {
		awaitApi();
		assertEquals(401, call("/customers", null, null, null).status());
		List<String> invoices = new ArrayList<>();

		String contested = newInvoice();
		invoices.add(contested);
		List<Reply> replies = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(20)) {
			List<Future<Reply>> calls = new ArrayList<>();
			for (int i = 0; i < 20; i++) calls.add(executor.submit(() -> pay(contested, "tok_success", UuidV7.generate().toString())));
			for (var future : calls) replies.add(future.get());
		}
		var accepted = replies.stream().filter(r -> r.status() == 202).toList();
		assertEquals(1, accepted.size(), replies.toString());
		assertTrue(replies.stream().allMatch(r -> r.status() == 202 || r.status() == 409));
		String winner = accepted.get(0).body().get("payment_attempt_id").asText();
		assertEquals("succeeded", awaitOutcome(winner).get("status").asText());
		assertEquals("1|1", processorOperations(winner));
		assertEquals("paid", call("/invoices/" + contested).body().get("state").asText());
		var again = pay(contested, "tok_success", UuidV7.generate().toString());
		assertEquals(409, again.status());
		assertEquals("invoice_already_paid", again.body().get("error").get("code").asText());
		System.out.println("concurrent tok_success: 1/20 accepted, paid, one processor operation PASS");

		for (var entry : TOKENS.entrySet()) {
			String token = entry.getKey();
			Expected expected = entry.getValue();
			String invoice = newInvoice();
			invoices.add(invoice);
			String key = UuidV7.generate().toString();
			long started = System.nanoTime();
			var first = pay(invoice, token, key);
			double acceptance = (System.nanoTime() - started) / 1e9;
			assertEquals(202, first.status(), first.body().toString());
			assertEquals("pending", first.body().get("status").asText());
			assertTrue(acceptance < 2, "acceptance took " + acceptance + "s");
			var outcome = awaitOutcome(first.body().get("payment_attempt_id").asText());
			double resolved = (System.nanoTime() - started) / 1e9;
			assertEquals(expected.status(), outcome.get("status").asText(), outcome.toString());
			assertEquals(expected.failureCode(), outcome.get("failure_code").isNull() ? null : outcome.get("failure_code").asText());
			if (token.equals("tok_timeout")) assertTrue(resolved >= 29, "timeout resolved after " + resolved + "s");
			var replay = pay(invoice, token, key);
			assertEquals(202, replay.status());
			assertEquals(first.body(), replay.body());
			assertEquals("1|1", processorOperations(first.body().get("payment_attempt_id").asText()));
			assertEquals(expected.invoiceState(), call("/invoices/" + invoice).body().get("state").asText());
			System.out.printf("%-23s -> %-9s accepted in %.3fs, resolved after %.1fs, replay identical, one POST PASS%n", token, expected.status(), acceptance, resolved);
		}

		Map<String, String> ours = new HashMap<>();
		for (var event : call("/events?limit=100").body().get("data"))
			if (invoices.contains(event.get("payload").get("data").get("invoice_id").asText())) ours.put(event.get("id").asText(), event.get("event_type").asText());
		assertEquals(Set.of("invoice.created", "invoice.paid", "invoice.payment_failed"), new HashSet<>(ours.values()));
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		boolean delivered = false;
		while (!delivered && System.nanoTime() < deadline) {
			int count = 0;
			boolean all = true;
			for (var delivery : call("/webhook-deliveries?limit=100").body().get("data"))
				if (ours.containsKey(delivery.get("event_id").asText())) {
					count++;
					all &= delivery.get("status").asText().equals("delivered");
				}
			delivered = all && count == ours.size();
			if (!delivered) Thread.sleep(1000);
		}
		assertTrue(delivered, "not all webhook deliveries confirmed");
		var received = client.send(HttpRequest.newBuilder(URI.create(RECEIVER + "/events")).build(), HttpResponse.BodyHandlers.ofString());
		Set<String> verified = new HashSet<>();
		for (var event : JSON.readTree(received.body()).get("data")) verified.add(event.get("id").asText());
		assertTrue(verified.containsAll(ours.keySet()), "receiver is missing events");
		System.out.println("webhooks: " + ours.size() + " events delivered and signature-verified by receiver PASS");
	}
}
