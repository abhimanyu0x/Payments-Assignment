package dev.dodo.notifications;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Event(UUID id, EventType eventType, @JsonRawValue @Schema(implementation = Map.class) String payload, Instant createdAt) {
}
