package dev.dodo.receiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dodo.platform.UuidV7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@Import(WebhookReceiverTest.Database.class)
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {
		"spring.liquibase.enabled=true",
		"spring.liquibase.change-log=file:../app/src/main/resources/db/changelog/db.changelog-master.xml",
		"receiver.webhook-secret=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
	})
class WebhookReceiverTest {
	@TestConfiguration(proxyBeanMethods = false)
	static class Database {
		@Bean
		@ServiceConnection
		PostgreSQLContainer<?> postgres() {
			var root = Stream.iterate(Path.of("").toAbsolutePath(), Path::getParent).takeWhile(Objects::nonNull).filter(dir -> Files.exists(dir.resolve("docker-compose.yml"))).findFirst().orElseThrow();
			return new PostgreSQLContainer<>(System.getProperty("postgres.image")).withCopyFileToContainer(MountableFile.forHostPath(root.resolve("docker/init.sql")), "/docker-entrypoint-initdb.d/init.sql");
		}
	}

	static final ObjectMapper JSON = new ObjectMapper();
	@LocalServerPort
	int port;
	@Autowired
	SignatureVerifier verifier;
	@Autowired
	PostgreSQLContainer<?> postgres;
	private final HttpClient client = HttpClient.newHttpClient();

	HttpResponse<String> deliver(String id, String body, long timestamp, String signature) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/webhooks"))
			.header("Content-Type", "application/json")
			.header("X-Webhook-Id", id)
			.header("X-Webhook-Timestamp", Long.toString(timestamp))
			.header("X-Webhook-Signature", signature)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	String signed(String body, long timestamp) {
		return "v1=" + verifier.sign(Long.toString(timestamp), body.getBytes(StandardCharsets.UTF_8));
	}

	long received(UUID id) throws Exception {
		var events = JSON.readTree(client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events")).build(), HttpResponse.BodyHandlers.ofString()).body()).get("data");
		long count = 0;
		for (var event : events) if (event.get("id").asText().equals(id.toString())) count++;
		return count;
	}

	String event(UUID id) throws Exception {
		return JSON.writeValueAsString(Map.of("id", id, "type", "invoice.paid", "data", Map.of()));
	}

	@Test
	void validSignatureIsStoredOnceAndRedeliveryIsFlaggedDuplicate() throws Exception {
		UUID id = UuidV7.generate();
		long now = Instant.now().getEpochSecond();
		String body = event(id);
		var first = deliver(id.toString(), body, now, signed(body, now));
		assertEquals(200, first.statusCode());
		assertFalse(JSON.readTree(first.body()).get("duplicate").asBoolean());
		var again = deliver(id.toString(), body, now, signed(body, now));
		assertEquals(200, again.statusCode());
		assertTrue(JSON.readTree(again.body()).get("duplicate").asBoolean());
		assertEquals(1, received(id));
		var listed = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events")).build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(id.toString(), JSON.readTree(listed.body()).get("data").get(0).get("id").asText());
	}

	@Test
	void badSignatureIsRejected() throws Exception {
		UUID id = UuidV7.generate();
		long now = Instant.now().getEpochSecond();
		assertEquals(400, deliver(id.toString(), event(id), now, "v1=bad").statusCode());
		String body = event(id);
		assertEquals(400, deliver(id.toString(), body.replace("paid", "PAID"), now, signed(body, now)).statusCode());
	}

	@Test
	void staleAndFutureTimestampsAreRejected() throws Exception {
		UUID id = UuidV7.generate();
		String body = event(id);
		for (long offset : new long[] {-600, 600}) {
			long timestamp = Instant.now().getEpochSecond() + offset;
			assertEquals(400, deliver(id.toString(), body, timestamp, signed(body, timestamp)).statusCode());
		}
	}

	@Test
	void headerIdMustMatchSignedBody() throws Exception {
		UUID id = UuidV7.generate();
		long now = Instant.now().getEpochSecond();
		String body = event(id);
		assertEquals(400, deliver(UuidV7.generate().toString(), body, now, signed(body, now)).statusCode());
		assertEquals(0, received(id));
	}

	@Test
	void oversizedBodyIsRejectedBeforeVerification() throws Exception {
		UUID id = UuidV7.generate();
		long now = Instant.now().getEpochSecond();
		String body = "x".repeat(70_000);
		assertEquals(413, deliver(id.toString(), body, now, signed(body, now)).statusCode());
	}

	@Test
	void runsOnlyAgainstItsOwnThrowawayDatabase(@Autowired JdbcConnectionDetails connection) {
		assertEquals(postgres.getJdbcUrl(), connection.getJdbcUrl());
		assertNotEquals("dodo", postgres.getDatabaseName());
	}
}
