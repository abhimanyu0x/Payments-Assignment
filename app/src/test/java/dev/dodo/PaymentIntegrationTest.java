package dev.dodo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dodo.billing.InvoiceEntity;
import dev.dodo.billing.InvoiceRepository;
import dev.dodo.common.Messages;
import dev.dodo.identity.ApiKeyService;
import dev.dodo.identity.BusinessEntity;
import dev.dodo.identity.BusinessRepository;
import dev.dodo.notifications.EventType;
import dev.dodo.notifications.WebhookEvents;
import dev.dodo.payments.PaymentAttemptEntity;
import dev.dodo.payments.PaymentAttemptRepository;
import dev.dodo.payments.PaymentProcessorClient;
import dev.dodo.payments.PaymentService;
import dev.dodo.payments.PaymentStatus;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestPropertySource(properties = {
	"app.psp-timeout=150ms",
	"app.payment-lease=0s",
	"app.payment-review-interval=0s",
	"app.payment-retry-delays=0s,0s,0s,0s,0s,0s"
})
class PaymentIntegrationTest extends IntegrationTest {
	static final Map<String, String> SUCCESS = token("tok_success");
	static final FakeProcessor psp = FakeProcessor.start();

	@DynamicPropertySource
	static void processor(DynamicPropertyRegistry r) {
		r.add("app.psp-url", psp::url);
	}

	@Autowired
	PaymentAttemptRepository attempts;
	@Autowired
	PaymentProcessorClient processor;
	@Autowired
	PaymentService service;
	@Autowired
	InvoiceRepository invoices;
	@Autowired
	BusinessRepository businesses;
	@Autowired
	ApiKeyService apiKeys;
	@MockitoSpyBean
	WebhookEvents events;

	@BeforeEach
	void prepare() {
		psp.reset();
		for (var leftover = attempts.claim(); leftover.isPresent(); leftover = attempts.claim())
			service.finish(leftover.get(), new PaymentProcessorClient.Result(PaymentStatus.FAILED, null, "test_cleanup"));
	}

	@AfterAll
	static void stop() {
		psp.stop();
	}

	static Map<String, String> token(String token) {
		return Map.of("card_token", token);
	}

	HttpResponse<String> pay(String invoice, Object body, String idempotencyKey) throws Exception {
		return send(KEY, "/invoices/" + invoice + "/pay", body, idempotencyKey);
	}

	String invoiceState(String invoice) throws Exception {
		return get("/invoices/" + invoice).get("state").asText();
	}

	List<JsonNode> history(String invoice) throws Exception {
		var list = new ArrayList<JsonNode>();
		get("/invoices/" + invoice + "/payment-attempts").get("data").forEach(list::add);
		return list;
	}

	List<String> eventTypes(String invoice) throws Exception {
		var types = new ArrayList<String>();
		get("/events?limit=100").get("data").forEach(e -> {
			if (e.get("payload").get("data").get("invoice_id").asText().equals(invoice)) types.add(e.get("event_type").asText());
		});
		return types;
	}

	@Test
	void concurrentRequestsAcceptOneCharge() throws Exception {
		String invoice = invoice();
		var start = new CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<HttpResponse<String>>> calls = new ArrayList<>();
			for (int i = 0; i < 20; i++)
				calls.add(executor.submit(() -> {
					start.await();
					return pay(invoice, SUCCESS, key());
				}));
			start.countDown();
			int accepted = 0;
			for (var call : calls) {
				int status = call.get(15, TimeUnit.SECONDS).statusCode();
				assertTrue(status == 202 || status == 409);
				if (status == 202) accepted++;
			}
			assertEquals(1, accepted);
		}
		var claim = attempts.claim().orElseThrow();
		service.finish(claim, processor.execute(claim));
		assertEquals(1, psp.posts.get());
		assertEquals(1, psp.operations.size());
		assertEquals("paid", invoiceState(invoice));
		var history = history(invoice);
		assertEquals(1, history.size());
		assertEquals("succeeded", history.get(0).get("status").asText());
	}

	@Test
	void sameKeyReplaysOriginalResponseWithoutSecondPspCall() throws Exception {
		String invoice = invoice(), key = key();
		var first = pay(invoice, SUCCESS, key);
		assertEquals(202, first.statusCode());
		var claim = attempts.claim().orElseThrow();
		service.finish(claim, processor.execute(claim));
		var retry = pay(invoice, SUCCESS, key);
		assertEquals(202, retry.statusCode());
		assertEquals(first.body(), retry.body());
		assertEquals(1, psp.posts.get());
		assertTrue(attempts.claim().isEmpty());
		var changed = pay(invoice, token("tok_card_declined"), key);
		assertEquals(409, changed.statusCode());
		assertEquals("idempotency_key_conflict", errorCode(changed));
		var newKey = pay(invoice, SUCCESS, key());
		assertEquals(409, newKey.statusCode());
		assertEquals("invoice_already_paid", errorCode(newKey));
		assertEquals(1, psp.posts.get());
	}

	@Test
	void timeoutIsUnknownThenRecoveredWithoutSecondCharge() throws Exception {
		String invoice = invoice();
		psp.slow.set(true);
		assertEquals(202, pay(invoice, token("tok_timeout"), key()).statusCode());
		var claim = attempts.claim().orElseThrow();
		var outcome = processor.execute(claim);
		assertEquals(PaymentStatus.UNKNOWN, outcome.status());
		service.finish(claim, outcome);
		assertEquals("open", invoiceState(invoice));
		assertEquals("unknown", history(invoice).get(0).get("status").asText());
		assertEquals(409, pay(invoice, SUCCESS, key()).statusCode());
		var recovery = attempts.claim().orElseThrow();
		service.finish(recovery, processor.execute(recovery));
		assertEquals("paid", invoiceState(invoice));
		assertEquals(1, psp.posts.get());
	}

	@Test
	void lostSuccessBeforePersistenceRecoversSameOperation() throws Exception {
		String invoice = invoice();
		assertEquals(202, pay(invoice, SUCCESS, key()).statusCode());
		var abandoned = attempts.claim().orElseThrow();
		assertEquals(PaymentStatus.SUCCEEDED, processor.execute(abandoned).status());
		var recovery = attempts.claim().orElseThrow();
		assertEquals(abandoned.getId(), recovery.getId());
		service.finish(recovery, processor.execute(recovery));
		assertEquals(1, psp.posts.get());
		assertEquals("paid", invoiceState(invoice));
	}

	@Test
	void invoiceAttemptAndEventRollBackTogether() throws Exception {
		String invoice = invoice();
		var accepted = JSON.readTree(pay(invoice, SUCCESS, key()).body());
		var claim = attempts.claim().orElseThrow();
		var outcome = processor.execute(claim);
		doThrow(new IllegalStateException("Injected event write failure")).when(events).record(any(), eq(EventType.INVOICE_PAID), any(), any(), any());
		assertThrows(IllegalStateException.class, () -> service.finish(claim, outcome));
		assertEquals("open", invoiceState(invoice));
		assertEquals("pending", get("/payment-attempts/" + accepted.get("payment_attempt_id").asText()).get("status").asText());
		assertFalse(eventTypes(invoice).contains("invoice.paid"));
		reset(events);
		service.finish(claim, outcome);
		assertEquals("paid", invoiceState(invoice));
		assertTrue(eventTypes(invoice).contains("invoice.paid"));
	}

	@Test
	void sameKeyCannotAcceptTwoDifferentInvoices() throws Exception {
		String first = invoice(), second = invoice(), key = key();
		var start = new CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var a = executor.submit(() -> {
				start.await();
				return pay(first, SUCCESS, key);
			});
			var b = executor.submit(() -> {
				start.await();
				return pay(second, SUCCESS, key);
			});
			start.countDown();
			var statuses = List.of(a.get(10, TimeUnit.SECONDS).statusCode(), b.get(10, TimeUnit.SECONDS).statusCode());
			assertEquals(1, statuses.stream().filter(status -> status == 202).count());
			assertEquals(1, statuses.stream().filter(status -> status == 409).count());
		}
		assertEquals(1, history(first).size() + history(second).size());
	}

	@Test
	void processorErrorStaysUnknownUntilLookupConfirmsFailure() throws Exception {
		String invoice = invoice();
		psp.serverError.set(true);
		assertEquals(202, pay(invoice, token("tok_network_error"), key()).statusCode());
		var claim = attempts.claim().orElseThrow();
		var outcome = processor.execute(claim);
		assertEquals(PaymentStatus.UNKNOWN, outcome.status());
		service.finish(claim, outcome);
		assertEquals("open", invoiceState(invoice));
		assertEquals("payment_in_progress", errorCode(pay(invoice, SUCCESS, key())));
		var recovery = attempts.claim().orElseThrow();
		service.finish(recovery, processor.execute(recovery));
		var attempt = history(invoice).get(0);
		assertEquals("failed", attempt.get("status").asText());
		assertEquals("processor_error", attempt.get("failure_code").asText());
		assertEquals("open", invoiceState(invoice));
		assertEquals(1, psp.posts.get());
		assertEquals(List.of("invoice.payment_failed"), eventTypes(invoice).stream().filter(type -> !type.equals("invoice.created")).toList());
		assertEquals(202, pay(invoice, SUCCESS, key()).statusCode());
	}

	@Test
	void staleWorkerCannotOverwriteNewerClaim() throws Exception {
		String invoice = invoice();
		assertEquals(202, pay(invoice, SUCCESS, key()).statusCode());
		var stale = attempts.claim().orElseThrow();
		var success = processor.execute(stale);
		var fresh = attempts.claim().orElseThrow();
		service.finish(stale, success);
		assertEquals("pending", history(invoice).get(0).get("status").asText());
		service.finish(fresh, processor.execute(fresh));
		service.finish(stale, new PaymentProcessorClient.Result(PaymentStatus.FAILED, null, "late_failure"));
		assertEquals("succeeded", history(invoice).get(0).get("status").asText());
		assertEquals("paid", invoiceState(invoice));
		assertEquals(1, psp.posts.get());
		assertEquals(1, eventTypes(invoice).stream().filter("invoice.paid"::equals).count());
	}

	@Test
	void exhaustedRecoveryIsFlaggedThenSettledByLookupWithoutCharging() throws Exception {
		String invoice = invoice();
		assertEquals(202, pay(invoice, token("tok_timeout"), key()).statusCode());
		for (int round = 0; round <= 6; round++) {
			var claim = attempts.claim().orElseThrow();
			service.finish(claim, PaymentProcessorClient.Result.unknown("psp_transport_error"));
		}
		var flagged = history(invoice).get(0);
		assertEquals("unknown", flagged.get("status").asText());
		assertTrue(flagged.get("review_required").asBoolean());
		assertEquals("payment_in_progress", errorCode(pay(invoice, SUCCESS, key())));
		var review = attempts.claim().orElseThrow();
		assertTrue(review.isReviewRequired());
		service.finish(review, processor.execute(review));
		var settled = history(invoice).get(0);
		assertEquals("failed", settled.get("status").asText());
		assertEquals("processor_error", settled.get("failure_code").asText());
		assertEquals(0, psp.posts.get());
		assertEquals("open", invoiceState(invoice));
		assertTrue(attempts.claim().isEmpty());
		assertEquals(202, pay(invoice, SUCCESS, key()).statusCode());
	}

	@Test
	void otherTenantCannotSeeOrPayAndRevokedKeyIsRejected() throws Exception {
		var other = businesses.save(BusinessEntity.builder().name("Other").build());
		assertEquals(7, other.getId().version());
		assertThrows(IllegalArgumentException.class, () -> apiKeys.issue(UUID.fromString(key())));
		var otherKey = apiKeys.issue(other.getId());
		String otherApiKey = otherKey.apiKey();
		String invoice = invoice();
		assertEquals(404, send(otherApiKey, "/invoices/" + invoice, null, null).statusCode());
		assertEquals(404, send(otherApiKey, "/invoices/" + invoice + "/pay", SUCCESS, key()).statusCode());
		assertFalse(send(otherApiKey, "/invoices", null, null).body().contains(invoice));
		assertTrue(history(invoice).isEmpty());
		assertTrue(apiKeys.revoke(other.getId(), otherKey.id()));
		var revoked = send(otherApiKey, "/invoices", null, null);
		assertEquals(401, revoked.statusCode());
		assertEquals("unauthorized", errorCode(revoked));
		assertEquals(401, send("demo.wrong-secret", "/invoices", null, null).statusCode());
	}

	@Test
	void securityHeadersCorsMethodsAndUnknownPathsAreLockedDown() throws Exception {
		var ok = raw("GET", "/api/v1/customers", null, null);
		assertEquals(200, ok.statusCode());
		var headers = ok.headers();
		assertEquals("nosniff", headers.firstValue("X-Content-Type-Options").orElse(""));
		assertEquals("DENY", headers.firstValue("X-Frame-Options").orElse(""));
		assertEquals("no-referrer", headers.firstValue("Referrer-Policy").orElse(""));
		assertEquals("same-origin", headers.firstValue("Cross-Origin-Opener-Policy").orElse(""));
		assertEquals("same-origin", headers.firstValue("Cross-Origin-Resource-Policy").orElse(""));
		assertTrue(headers.firstValue("Content-Security-Policy").orElse("").startsWith("default-src 'none'"));
		assertTrue(headers.firstValue("Permissions-Policy").orElse("").contains("camera=()"));
		assertTrue(headers.firstValue("Cache-Control").orElse("").contains("no-store"));

		var unauthenticated = anonymous("/api/v1/customers");
		assertEquals(401, unauthenticated.statusCode());
		assertEquals("Bearer", unauthenticated.headers().firstValue("WWW-Authenticate").orElse(""));
		var unknown = anonymous("/");
		assertEquals(401, unknown.statusCode());
		assertEquals("unauthorized", errorCode(unknown));
		assertFalse(unknown.body().contains("timestamp"));

		int customersBefore = get("/customers?limit=100").get("data").size();
		var crossOrigin = raw("POST", "/api/v1/customers", "https://evil.example", JSON.writeValueAsString(Map.of("name", "x", "email", "x@example.com")));
		assertEquals(403, crossOrigin.statusCode());
		assertTrue(crossOrigin.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
		assertEquals(customersBefore, get("/customers?limit=100").get("data").size());

		for (String method : List.of("PUT", "DELETE", "PATCH", "OPTIONS", "TRACE")) {
			var rejected = raw(method, "/api/v1/customers", null, null);
			assertEquals(400, rejected.statusCode(), method);
			assertEquals("invalid_request", errorCode(rejected), method);
		}
		assertEquals(400, raw("POST", "/api/v1/customers", null, "{\"name\":\"a\",\"name\":\"b\",\"email\":\"x@example.com\"}").statusCode());
		var oversizedKey = client.send(HttpRequest.newBuilder(uri("/api/v1/customers")).header("Authorization", "Bearer demo." + "x".repeat(300)).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(401, oversizedKey.statusCode());
		var lowercaseScheme = client.send(HttpRequest.newBuilder(uri("/api/v1/customers")).header("Authorization", "bearer " + KEY).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, lowercaseScheme.statusCode());
		assertEquals(401, anonymous("/actuator/env").statusCode());
		var health = anonymous("/actuator/health");
		assertEquals(200, health.statusCode());
		assertEquals("{\"status\":\"UP\"}", health.body());
		var docs = anonymous("/swagger-ui/index.html");
		assertEquals(200, docs.statusCode());
		assertTrue(docs.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
	}

	@Test
	void idsAreTimeOrderedUuidV7AndEnumsKeepLowercaseWireValues() throws Exception {
		List<String> created = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			String id = customer();
			assertEquals(7, UUID.fromString(id).version());
			created.add(id);
			Thread.sleep(2);
		}
		assertEquals(created, created.stream().sorted().toList());
		var newestFirst = new ArrayList<String>();
		get("/customers?limit=3").get("data").forEach(c -> newestFirst.add(c.get("id").asText()));
		assertEquals(created.reversed(), newestFirst);

		String invoice = invoice();
		assertEquals(7, UUID.fromString(invoice).version());
		assertEquals("open", invoiceState(invoice));
		String key = key();
		var accepted = JSON.readTree(pay(invoice, SUCCESS, key).body());
		assertEquals("pending", accepted.get("status").asText());
		assertEquals(7, UUID.fromString(accepted.get("payment_attempt_id").asText()).version());
		assertEquals(200, request("/invoices?state=open", null).statusCode());
		assertEquals(400, request("/invoices?state=OPEN", null).statusCode());
		assertEquals(202, pay(invoice, SUCCESS, key).statusCode());
	}

	@Test
	void webhookResultsAreListedPerInvoiceWithTheirEventType() throws Exception {
		assertEquals(201, request("/webhook-endpoints", Map.of("url", "http://demo-receiver:8090/webhooks")).statusCode());
		String first = invoice(), second = invoice();
		var page = get("/webhook-deliveries?invoice_id=" + first).get("data");
		assertEquals(1, page.size());
		assertEquals("invoice.created", page.get(0).get("event_type").asText());
		assertEquals(first, page.get(0).get("invoice_id").asText());
		assertEquals("pending", page.get(0).get("status").asText());
		assertNotEquals(second, page.get(0).get("invoice_id").asText());
	}

	@Test
	void errorMessagesArePlainAndRevealNoInternals() throws Exception {
		var invalid = request("/invoices", Map.of("customer_id", customer(), "due_date", "2026-12-01", "items", List.of(Map.of("description", "", "quantity", 0))));
		assertEquals(422, invalid.statusCode());
		var error = JSON.readTree(invalid.body()).get("error");
		assertEquals("validation_failed", error.get("code").asText());
		assertEquals(Messages.DETAILS_INVALID, error.get("message").asText());
		var messages = new ArrayList<String>();
		error.get("details").forEach(d -> messages.add(d.get("message").asText()));
		assertTrue(messages.containsAll(List.of(Messages.DESCRIPTION_REQUIRED, Messages.QUANTITY_RANGE, Messages.PRICE_REQUIRED)), messages.toString());
		var noKey = pay(invoice(), SUCCESS, null);
		assertEquals("idempotency_key_required", errorCode(noKey));
		messages.add(JSON.readTree(noKey.body()).get("error").get("message").asText());
		messages.add(JSON.readTree(send("demo.wrong", "/invoices", null, null).body()).get("error").get("message").asText());
		for (String message : messages) {
			assertFalse(message.matches(".*[;:()\\[\\]_'\"].*"), message);
			assertFalse(message.toLowerCase().contains("idempotency") || message.toLowerCase().contains("database") || message.contains("cents"), message);
		}
	}

	@Test
	void databaseRulesHoldWhenSavingThroughRepositories() throws Exception {
		UUID customer = UUID.fromString(customer());
		var zeroTotal = assertThrows(DataIntegrityViolationException.class, () -> invoices.save(InvoiceEntity.builder().businessId(DEMO_BUSINESS).customerId(customer).totalAmountCents(0).dueDate(LocalDate.now()).build()));
		assertTrue(zeroTotal.getMessage().contains("invoices_total_amount_cents_check"), zeroTotal.getMessage());

		UUID invoice = UUID.fromString(invoice());
		var first = attempts.save(attempt(invoice, 5000));
		assertEquals(7, first.getId().version());
		var second = assertThrows(DataIntegrityViolationException.class, () -> attempts.save(attempt(invoice, 5000)));
		assertTrue(second.getMessage().contains("one_unresolved_attempt"), second.getMessage());

		var zeroAmount = assertThrows(DataIntegrityViolationException.class, () -> attempts.save(attempt(UUID.fromString(invoice()), 0)));
		assertTrue(zeroAmount.getMessage().contains("payment_attempts_amount_cents_check"), zeroAmount.getMessage());
	}

	PaymentAttemptEntity attempt(UUID invoice, long amount) {
		var now = Instant.now();
		return PaymentAttemptEntity.builder()
			.businessId(DEMO_BUSINESS)
			.invoiceId(invoice)
			.idempotencyKey(key())
			.requestFingerprint("fingerprint")
			.mockCardToken("tok_success")
			.amountCents(amount)
			.nextAttemptAt(now)
			.recoveryDeadlineAt(now.plusSeconds(600))
			.build();
	}

	@Test
	void everyResponseCarriesAUuidV7TraceIdThatMatchesTheErrorBody() throws Exception {
		String trace = request("/customers", null).headers().firstValue("X-Request-Id").orElseThrow();
		assertEquals(7, UUID.fromString(trace.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")).version());
		var denied = anonymous("/api/v1/customers");
		String deniedTrace = denied.headers().firstValue("X-Request-Id").orElseThrow();
		assertEquals(deniedTrace, JSON.readTree(denied.body()).path("error").path("request_id").asText());
		assertNotEquals(trace, deniedTrace);
		String foreign = "0af7651916cd43dd8448eb211c80319c";
		var spoofed = client.send(HttpRequest.newBuilder(uri("/api/v1/customers")).header("Authorization", "Bearer " + KEY).header("traceparent", "00-" + foreign + "-b7ad6b7169203331-01").build(), HttpResponse.BodyHandlers.ofString());
		assertNotEquals(foreign, spoofed.headers().firstValue("X-Request-Id").orElseThrow());
	}

	@Test
	void bodyLimitPageSizeAndCursorsAreCheckedThroughTheApi() throws Exception {
		var big = request("/customers", Map.of("name", "x".repeat(100_000), "email", "x@example.com"));
		assertEquals(413, big.statusCode());
		assertEquals("request_too_large", errorCode(big));
		var justOver = raw("POST", "/api/v1/customers", null, "x".repeat(64 * 1024 + 1));
		assertEquals(413, justOver.statusCode());
		assertEquals(justOver.headers().firstValue("X-Request-Id").orElseThrow(), JSON.readTree(justOver.body()).path("error").path("request_id").asText());
		for (String limit : List.of("0", "101", "abc")) {
			var page = request("/customers?limit=" + limit, null);
			assertEquals(422, page.statusCode(), limit);
			var detail = JSON.readTree(page.body()).path("error").path("details").get(0);
			assertEquals("limit", detail.get("field").asText());
			assertEquals(limit.equals("abc") ? Messages.VALUE_INVALID : Messages.PAGE_SIZE_INVALID, detail.get("message").asText());
		}
		var expired = request("/customers?cursor=bm90LWEtY3Vyc29y", null);
		assertEquals(400, expired.statusCode());
		assertEquals("invalid_cursor", errorCode(expired));
		for (int i = 0; i < 4; i++) customer();
		var first = get("/customers?limit=2");
		var second = get("/customers?limit=2&cursor=" + first.get("next_cursor").asText());
		var seen = new ArrayList<String>();
		first.get("data").forEach(c -> seen.add(c.get("id").asText()));
		second.get("data").forEach(c -> seen.add(c.get("id").asText()));
		assertEquals(4, seen.size());
		assertEquals(seen.stream().sorted(Comparator.reverseOrder()).toList(), seen);
		assertEquals(400, request("/invoices?cursor=" + first.get("next_cursor").asText(), null).statusCode());
	}

	@Test
	void webhookEndpointsMustBePublicHttpsUniqueAndCanBeDeactivated() throws Exception {
		for (String url : List.of("http://example.com/hooks", "https://127.0.0.1/hooks", "https://10.0.0.5/hooks", "https://169.254.169.254/latest", "https://[::1]/hooks", "https://user:secret@93.184.216.34/hooks", "ftp://93.184.216.34/hooks", "https://[64:ff9b::7f00:1]/hooks", "https://no-such-host.invalid/hooks")) {
			var rejected = request("/webhook-endpoints", Map.of("url", url));
			assertEquals(422, rejected.statusCode(), url);
			assertEquals(Messages.WEBHOOK_ADDRESS_NOT_ALLOWED, JSON.readTree(rejected.body()).path("error").path("message").asText());
		}
		var created = request("/webhook-endpoints", Map.of("url", "https://93.184.216.34/hooks"));
		assertEquals(201, created.statusCode(), created.body());
		String id = JSON.readTree(created.body()).get("id").asText();
		var duplicate = request("/webhook-endpoints", Map.of("url", "HTTPS://93.184.216.34/hooks"));
		assertEquals(409, duplicate.statusCode());
		assertEquals("webhook_endpoint_exists", errorCode(duplicate));
		var deactivated = request("/webhook-endpoints/" + id + "/deactivate", Map.of());
		assertEquals(200, deactivated.statusCode());
		assertFalse(JSON.readTree(deactivated.body()).get("active").asBoolean());
		var again = request("/webhook-endpoints", Map.of("url", "https://93.184.216.34/hooks"));
		assertEquals(201, again.statusCode());
		assertEquals(200, request("/webhook-endpoints/" + JSON.readTree(again.body()).get("id").asText() + "/deactivate", Map.of()).statusCode());
	}

	@Test
	void overdueAttemptNeverSentIsFlaggedThenChargedExactlyOnce() throws Exception {
		UUID invoice = UUID.fromString(invoice());
		var now = Instant.now();
		var overdue = attempts.save(PaymentAttemptEntity.builder()
			.businessId(DEMO_BUSINESS)
			.invoiceId(invoice)
			.idempotencyKey(key())
			.requestFingerprint("fingerprint")
			.mockCardToken("tok_timeout")
			.amountCents(5000)
			.nextAttemptAt(now.plusSeconds(3600))
			.recoveryDeadlineAt(now.minusSeconds(1))
			.build());
		attempts.flagOverdue();
		var flagged = get("/payment-attempts/" + overdue.getId());
		assertEquals("unknown", flagged.get("status").asText());
		assertTrue(flagged.get("review_required").asBoolean());
		assertEquals("recovery_budget_exhausted", flagged.get("last_error_code").asText());
		var review = attempts.claim().orElseThrow();
		assertEquals(overdue.getId(), review.getId());
		service.finish(review, processor.execute(review));
		assertEquals("succeeded", get("/payment-attempts/" + overdue.getId()).get("status").asText());
		assertEquals(1, psp.posts.get());
		assertEquals("paid", invoiceState(invoice.toString()));
	}
}
