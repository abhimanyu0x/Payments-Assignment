package dev.dodo.notifications;

import dev.dodo.http.Json;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WebhookEvents {
  private final JdbcTemplate jdbc;
  private final Json json;

  public WebhookEvents(JdbcTemplate jdbc, Json json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void record(UUID business, String type, UUID source, Map<String, Object> data) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("An event must join a business transaction.");
    UUID id = UUID.randomUUID();
    String payload =
        json.write(
            Map.of("id", id, "type", type, "created_at", Instant.now().toString(), "data", data));
    int added =
        jdbc.update(
            "INSERT INTO notifications.events(id,business_id,event_type,source_id,payload) VALUES"
                + " (?,?,?,?,?) ON CONFLICT(business_id,event_type,source_id) DO NOTHING",
            id,
            business,
            type,
            source,
            payload);
    if (added == 0) return;
    for (var endpoint :
        jdbc.queryForList(
            "SELECT id FROM notifications.webhook_endpoints WHERE business_id=? AND active",
            business))
      jdbc.update(
          "INSERT INTO notifications.webhook_deliveries(id,business_id,event_id,endpoint_id) VALUES"
              + " (?,?,?,?)",
          UUID.randomUUID(),
          business,
          id,
          endpoint.get("id"));
  }
}
