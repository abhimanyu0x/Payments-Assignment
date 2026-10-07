package dev.dodo.notifications;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!migrate")
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
public class DemoEndpoint implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final SecretCipher cipher;
  private final String url;

  public DemoEndpoint(
      JdbcTemplate jdbc, SecretCipher cipher, @Value("${app.webhook-url}") String url) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.url = url;
  }

  public void run(ApplicationArguments args) {
    jdbc.update(
        "INSERT INTO notifications.webhook_endpoints(id,business_id,url,secret_ciphertext) VALUES"
            + " (?,?,?,?) ON CONFLICT(id) DO NOTHING",
        UUID.fromString("33333333-3333-4333-8333-333333333333"),
        UUID.fromString("11111111-1111-4111-8111-111111111111"),
        url,
        cipher.encrypt(Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=")));
  }
}
