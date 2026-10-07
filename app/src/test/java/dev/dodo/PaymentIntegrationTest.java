package dev.dodo;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import dev.dodo.payments.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=true",
      "app.workers-enabled=false",
      "app.demo-seed=false",
      "app.psp-timeout-ms=150",
      "app.payment-retry-delays=0,0,0,0,0,0"
    })
class PaymentIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17.6-alpine").withInitScript("roles.sql");

  static final ObjectMapper JSON = new ObjectMapper();
  static final AtomicInteger posts = new AtomicInteger();
  static final Map<String, String> operations = new ConcurrentHashMap<>();
  static final AtomicBoolean slow = new AtomicBoolean();
  static final HttpServer psp;

  static {
    try {
      psp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      psp.setExecutor(Executors.newCachedThreadPool());
      psp.createContext(
          "/payments",
          exchange -> {
            try {
              String id;
              if (exchange.getRequestMethod().equals("POST")) {
                var request = JSON.readTree(exchange.getRequestBody());
                id = request.get("operation_id").asText();
                posts.incrementAndGet();
                operations.putIfAbsent(id, "{\"status\":\"succeeded\",\"psp_ref\":\"" + id + "\"}");
                if (slow.get()) Thread.sleep(500);
              } else {
                id = exchange.getRequestURI().getPath().substring("/payments/".length());
              }
              String body = operations.get(id);
              byte[] bytes =
                  (body == null ? "{}" : body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
              exchange.getResponseBody().write(bytes);
            } catch (Exception ignored) {
            } finally {
              exchange.close();
            }
          });
      psp.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.psp-url", () -> "http://127.0.0.1:" + psp.getAddress().getPort());
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired PaymentProcessorClient processor;
  @Autowired PaymentService service;
  private final HttpClient client = HttpClient.newHttpClient();
  static final String KEY = "demo.local-demo-secret-change-for-real-use";

  @BeforeEach
  void reset() {
    posts.set(0);
    operations.clear();
    slow.set(false);
    jdbc.execute(
        "TRUNCATE"
            + " notifications.webhook_deliveries,notifications.events,payments.payment_attempts,billing.invoice_items,billing.invoices,customers.customers"
            + " CASCADE");
  }

  @AfterAll
  static void stop() {
    psp.stop(0);
  }

  HttpResponse<String> request(String path, String body, String key) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
            .header("Authorization", "Bearer " + KEY)
            .header("Content-Type", "application/json");
    if (key != null) b.header("Idempotency-Key", key);
    return client.send(
        b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String invoice() throws Exception {
    var customer =
        request("/customers", "{\"name\":\"Test\",\"email\":\"test@example.com\"}", null);
    assertEquals(201, customer.statusCode(), customer.body());
    String id = JSON.readTree(customer.body()).get("id").asText();
    var response =
        request(
            "/invoices",
            "{\"customer_id\":\""
                + id
                + "\",\"due_date\":\"2026-10-20\",\"items\":[{\"description\":\"Work\",\"quantity\":2,\"unit_amount_cents\":2500}]}",
            null);
    assertEquals(201, response.statusCode(), response.body());
    return JSON.readTree(response.body()).get("id").asText();
  }

  @Test
  void concurrentRequestsAcceptOneCharge() throws Exception {
    String invoice = invoice();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<HttpResponse<String>>> calls = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        calls.add(
            executor.submit(
                () -> {
                  start.await();
                  return request(
                      "/invoices/" + invoice + "/pay",
                      "{\"card_token\":\"tok_success\"}",
                      UUID.randomUUID().toString());
                }));
      start.countDown();
      int accepted = 0;
      for (var f : calls) {
        int status = f.get(15, TimeUnit.SECONDS).statusCode();
        assertTrue(status == 202 || status == 409);
        if (status == 202) accepted++;
      }
      assertEquals(1, accepted);
    }
    var claim = attempts.claim().orElseThrow();
    service.finish(claim, processor.execute(claim));
    assertEquals(1, posts.get());
    assertEquals(1, operations.size());
    assertEquals(
        "paid",
        jdbc.queryForObject(
            "SELECT state FROM billing.invoices WHERE id=?",
            String.class,
            UUID.fromString(invoice)));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM payments.payment_attempts WHERE status='succeeded'",
            Integer.class));
  }

  @Test
  void sameKeyReplaysOriginalResponseWithoutSecondPspCall() throws Exception {
    String id = invoice(), key = UUID.randomUUID().toString();
    var first = request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_success\"}", key);
    assertEquals(202, first.statusCode());
    var claim = attempts.claim().orElseThrow();
    service.finish(claim, processor.execute(claim));
    var retry = request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_success\"}", key);
    assertEquals(202, retry.statusCode());
    assertEquals(first.body(), retry.body());
    assertEquals(1, posts.get());
    assertTrue(attempts.claim().isEmpty());
    var changed =
        request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_card_declined\"}", key);
    assertEquals(409, changed.statusCode());
  }

  @Test
  void timeoutIsUnknownThenRecoveredWithoutSecondCharge() throws Exception {
    String id = invoice();
    slow.set(true);
    var accepted =
        request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_timeout\"}", "timeout-case");
    assertEquals(202, accepted.statusCode());
    var claim = attempts.claim().orElseThrow();
    var outcome = processor.execute(claim);
    assertEquals("unknown", outcome.status());
    service.finish(claim, outcome);
    assertEquals(
        "open",
        jdbc.queryForObject(
            "SELECT state FROM billing.invoices WHERE id=?", String.class, UUID.fromString(id)));
    assertEquals(
        409,
        request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_success\"}", "new-key")
            .statusCode());
    var recovery = attempts.claim().orElseThrow();
    service.finish(recovery, processor.execute(recovery));
    assertEquals(
        "paid",
        jdbc.queryForObject(
            "SELECT state FROM billing.invoices WHERE id=?", String.class, UUID.fromString(id)));
    assertEquals(1, posts.get());
  }

  @Test
  void lostSuccessBeforePersistenceRecoversSameOperation() throws Exception {
    String id = invoice();
    request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_success\"}", "crash-case");
    var abandoned = attempts.claim().orElseThrow();
    assertEquals(
        "succeeded",
        processor
            .execute(abandoned)
            .status()); // Deliberately omit finalization: simulates process loss.
    jdbc.update("UPDATE payments.payment_attempts SET lease_expires_at=now()-interval '1 second'");
    var recovery = attempts.claim().orElseThrow();
    service.finish(recovery, processor.execute(recovery));
    assertEquals(1, posts.get());
    assertEquals(
        "paid",
        jdbc.queryForObject(
            "SELECT state FROM billing.invoices WHERE id=?", String.class, UUID.fromString(id)));
  }

  @Test
  void invoiceAttemptAndEventRollBackTogether() throws Exception {
    String id = invoice();
    request("/invoices/" + id + "/pay", "{\"card_token\":\"tok_success\"}", "rollback-case");
    var claim = attempts.claim().orElseThrow();
    var outcome = processor.execute(claim);
    jdbc.execute(
        "CREATE FUNCTION notifications.reject_paid_test() RETURNS trigger LANGUAGE plpgsql AS $$"
            + " BEGIN IF NEW.event_type='invoice.paid' THEN RAISE EXCEPTION 'injected event write"
            + " failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_paid_test BEFORE INSERT ON notifications.events FOR EACH ROW EXECUTE"
            + " FUNCTION notifications.reject_paid_test()");
    try {
      assertThrows(
          org.springframework.dao.DataAccessException.class, () -> service.finish(claim, outcome));
      assertEquals(
          "open",
          jdbc.queryForObject(
              "SELECT state FROM billing.invoices WHERE id=?", String.class, UUID.fromString(id)));
      assertEquals(
          "pending",
          jdbc.queryForObject(
              "SELECT status FROM payments.payment_attempts WHERE id=?",
              String.class,
              claim.get("id")));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM notifications.events WHERE event_type='invoice.paid'",
              Integer.class));
    } finally {
      jdbc.execute("DROP TRIGGER reject_paid_test ON notifications.events");
      jdbc.execute("DROP FUNCTION notifications.reject_paid_test()");
    }
    service.finish(claim, outcome);
    assertEquals(
        "paid",
        jdbc.queryForObject(
            "SELECT state FROM billing.invoices WHERE id=?", String.class, UUID.fromString(id)));
  }

  @Test
  void sameKeyCannotAcceptTwoDifferentInvoices() throws Exception {
    String first = invoice(), second = invoice();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a =
          executor.submit(
              () -> {
                start.await();
                return request(
                    "/invoices/" + first + "/pay",
                    "{\"card_token\":\"tok_success\"}",
                    "shared-key");
              });
      var b =
          executor.submit(
              () -> {
                start.await();
                return request(
                    "/invoices/" + second + "/pay",
                    "{\"card_token\":\"tok_success\"}",
                    "shared-key");
              });
      start.countDown();
      var statuses =
          List.of(
              a.get(10, TimeUnit.SECONDS).statusCode(), b.get(10, TimeUnit.SECONDS).statusCode());
      assertEquals(1, statuses.stream().filter(status -> status == 202).count());
      assertEquals(1, statuses.stream().filter(status -> status == 409).count());
    }
    assertEquals(
        1, jdbc.queryForObject("SELECT count(*) FROM payments.payment_attempts", Integer.class));
  }
}
