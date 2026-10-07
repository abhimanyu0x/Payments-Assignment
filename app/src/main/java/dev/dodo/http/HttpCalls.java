package dev.dodo.http;

import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class HttpCalls {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(1))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public HttpResponse<String> send(HttpRequest request, long deadlineMillis) throws Exception {
    var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    try {
      return future.get(deadlineMillis, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      future.cancel(true);
      throw e;
    }
  }

  public int deliver(HttpRequest request) throws Exception {
    var future = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    try {
      return future.get(5, TimeUnit.SECONDS).statusCode();
    } catch (Exception e) {
      future.cancel(true);
      throw e;
    }
  }
}
