package dev.dodo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dodo.platform.UuidV7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

@Import(TestDatabase.class)
@AutoConfigureObservability(metrics = false)
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {
		"spring.liquibase.enabled=true",
		"app.workers-enabled=false",
		"app.demo-seed=false",
		"app.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"server.tomcat.accesslog.enabled=false"
	})
public abstract class IntegrationTest {
	protected static final UUID DEMO_BUSINESS = UUID.fromString("01a11810-ce4f-76f3-9863-cbf7566684b5");
	protected static final String KEY = "demo.local-demo-secret-change-for-real-use";
	protected static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	protected int port;
	@Autowired
	protected PostgreSQLContainer<?> postgres;
	@Autowired
	private JdbcConnectionDetails connection;
	@Autowired
	private DataSource dataSource;
	protected final HttpClient client = HttpClient.newHttpClient();

	protected URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	protected HttpResponse<String> send(String apiKey, String path, Object body, String idempotencyKey) throws Exception {
		var request = HttpRequest.newBuilder(uri("/api/v1" + path)).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json");
		if (Objects.nonNull(idempotencyKey)) request.header("Idempotency-Key", idempotencyKey);
		request.method(Objects.isNull(body) ? "GET" : "POST", Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	protected HttpResponse<String> request(String path, Object body) throws Exception {
		return send(KEY, path, body, null);
	}

	protected HttpResponse<String> raw(String method, String path, String origin, String body) throws Exception {
		var request = HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + KEY).header("Content-Type", "application/json")
			.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (Objects.nonNull(origin)) request.header("Origin", origin);
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	protected HttpResponse<String> anonymous(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
	}

	protected JsonNode get(String path) throws Exception {
		var response = request(path, null);
		assertEquals(200, response.statusCode(), response.body());
		return JSON.readTree(response.body());
	}

	protected String errorCode(HttpResponse<String> response) throws Exception {
		return JSON.readTree(response.body()).path("error").path("code").asText();
	}

	protected String customer() throws Exception {
		var customer = request("/customers", Map.of("name", "Test", "email", "test@example.com"));
		assertEquals(201, customer.statusCode(), customer.body());
		return JSON.readTree(customer.body()).get("id").asText();
	}

	protected String invoice() throws Exception {
		var item = Map.of("description", "Work", "quantity", 2, "unit_amount_cents", 2500);
		var response = request("/invoices", Map.of("customer_id", customer(), "due_date", "2026-10-20", "items", List.of(item)));
		assertEquals(201, response.statusCode(), response.body());
		return JSON.readTree(response.body()).get("id").asText();
	}

	protected String key() {
		return UuidV7.generate().toString();
	}

	@Test
	void runsOnlyAgainstItsOwnThrowawayDatabase() throws Exception {
		assertEquals(postgres.getJdbcUrl(), connection.getJdbcUrl());
		try (var open = dataSource.getConnection()) {
			assertEquals(postgres.getDatabaseName(), open.getCatalog());
			assertNotEquals("dodo", open.getCatalog());
		}
	}
}
