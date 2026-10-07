package dev.dodo.identity;

import java.security.SecureRandom;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("key-admin")
public class KeyAdmin implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final ConfigurableApplicationContext context;

  public KeyAdmin(JdbcTemplate jdbc, ConfigurableApplicationContext context) {
    this.jdbc = jdbc;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    String operation = value(args, "operation");
    UUID business = UUID.fromString(value(args, "business-id"));
    if (operation.equals("create")) {
      UUID id = UUID.randomUUID();
      String prefix = id.toString();
      byte[] bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      jdbc.update(
          "INSERT INTO identity.api_keys(id,business_id,key_prefix,secret_hash) VALUES (?,?,?,?)",
          id,
          business,
          prefix,
          KeyHash.of(secret));
      System.out.println("New API key (shown once): " + prefix + "." + secret);
      System.out.println("Key ID: " + id);
    } else if (operation.equals("revoke")) {
      UUID key = UUID.fromString(value(args, "key-id"));
      int changed =
          jdbc.update(
              "UPDATE identity.api_keys SET revoked_at=now() WHERE id=? AND business_id=? AND"
                  + " revoked_at IS NULL",
              key,
              business);
      System.out.println("Keys revoked: " + changed);
    } else throw new IllegalArgumentException("operation must be create or revoke");
    SpringApplication.exit(context, () -> 0);
  }

  private String value(ApplicationArguments args, String name) {
    var values = args.getOptionValues(name);
    if (values == null || values.size() != 1)
      throw new IllegalArgumentException("Provide --" + name + "=...");
    return values.get(0);
  }
}
