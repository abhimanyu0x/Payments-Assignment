package dev.dodo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class FakeProcessor {
	private static final ObjectMapper JSON = new ObjectMapper();
	final AtomicInteger posts = new AtomicInteger();
	final Map<String, Map<String, String>> operations = new ConcurrentHashMap<>();
	final AtomicBoolean slow = new AtomicBoolean();
	final AtomicBoolean serverError = new AtomicBoolean();
	private final HttpServer server;

	private FakeProcessor(HttpServer server) {
		this.server = server;
	}

	static FakeProcessor start() {
		try {
			var processor = new FakeProcessor(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
			processor.server.setExecutor(Executors.newCachedThreadPool());
			processor.server.createContext("/payments", processor::handle);
			processor.server.start();
			return processor;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void reset() {
		posts.set(0);
		operations.clear();
		slow.set(false);
		serverError.set(false);
	}

	void stop() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			String id = "POST".equals(exchange.getRequestMethod()) ? charge(exchange) : exchange.getRequestURI().getPath().substring("/payments/".length());
			if (Objects.isNull(id)) {
				exchange.sendResponseHeaders(500, -1);
				return;
			}
			var body = operations.get(id);
			byte[] bytes = JSON.writeValueAsBytes(Objects.requireNonNullElse(body, Map.of()));
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(Objects.isNull(body) ? 404 : 200, bytes.length);
			exchange.getResponseBody().write(bytes);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String charge(HttpExchange exchange) throws IOException, InterruptedException {
		String id = JSON.readTree(exchange.getRequestBody()).get("operation_id").asText();
		posts.incrementAndGet();
		if (serverError.get()) {
			operations.putIfAbsent(id, Map.of("status", "failed", "code", "processor_error"));
			return null;
		}
		operations.putIfAbsent(id, Map.of("status", "succeeded", "psp_ref", id));
		if (slow.get()) Thread.sleep(500);
		return id;
	}
}
