package dev.dodo.receiver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(schema = "demo_receiver", name = "received_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReceivedEventEntity implements Persistable<UUID> {
	@Id
	@Column(name = "event_id")
	private UUID eventId;
	@Column(name = "event_type", nullable = false, columnDefinition = "text")
	private String eventType;
	@Column(nullable = false, columnDefinition = "text")
	private String payload;
	@CreatedDate
	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;
	@Transient
	@Builder.Default
	private boolean fresh = true;

	@Override
	public UUID getId() {
		return eventId;
	}

	@Override
	public boolean isNew() {
		return fresh;
	}

	@PostLoad
	@PostPersist
	void stored() {
		fresh = false;
	}
}
