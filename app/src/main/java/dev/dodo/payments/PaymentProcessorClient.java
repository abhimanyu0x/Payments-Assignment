package dev.dodo.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dodo.http.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessorClient {
  public record Result(String status, String reference, String failure) {
    public static Result unknown(String reason) {
      return new Result("unknown", null, reason);
    }
  }

  private final String base;
  private final long timeout;
  private final HttpCalls http;
  private final ObjectMapper mapper;
  private final Json json;
  private final PaymentAttemptRepository attempts;

  public PaymentProcessorClient(
      @Value("${app.psp-url}") String base,
      @Value("${app.psp-timeout-ms}") long timeout,
      HttpCalls http,
      ObjectMapper mapper,
      Json json,
      PaymentAttemptRepository attempts) {
    this.base = base;
    this.timeout = timeout;
    this.http = http;
    this.mapper = mapper;
    this.json = json;
    this.attempts = attempts;
  }

  public Result execute(Map<String, Object> claim) {
    try {
      UUID id = (UUID) claim.get("id");
      long version = ((Number) claim.get("claim_version")).longValue();
      if (version > 1) {
        var response =
            call(
                id,
                version,
                HttpRequest.newBuilder(URI.create(base + "/payments/" + id))
                    .timeout(Duration.ofMillis(timeout))
                    .GET()
                    .build());
        if (response.statusCode() != 404) return parse(response);
      }
      var body =
          Map.of(
              "operation_id",
              id,
              "amount_cents",
              claim.get("amount_cents"),
              "currency",
              "USD",
              "card_token",
              claim.get("mock_card_token"));
      return parse(
          call(
              id,
              version,
              HttpRequest.newBuilder(URI.create(base + "/payments"))
                  .timeout(Duration.ofMillis(timeout))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(json.write(body)))
                  .build()));
    } catch (Exception e) {
      return Result.unknown("psp_transport_error");
    }
  }

  private HttpResponse<String> call(UUID id, long version, HttpRequest request) throws Exception {
    attempts.countCall(id, version);
    return http.send(request, timeout);
  }

  private Result parse(HttpResponse<String> response) throws Exception {
    if (response.statusCode() != 200) return Result.unknown("psp_http_" + response.statusCode());
    var value = mapper.readTree(response.body());
    String status = value.path("status").asText();
    if (status.equals("succeeded") && !value.path("psp_ref").asText().isBlank())
      return new Result(status, value.path("psp_ref").asText(), null);
    if (status.equals("failed") && !value.path("code").asText().isBlank())
      return new Result(status, null, value.path("code").asText());
    return Result.unknown(
        status.equals("pending") ? "confirmation_pending" : "invalid_psp_response");
  }
}
