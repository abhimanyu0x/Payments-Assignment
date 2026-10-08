package dev.dodo.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(schema = "notifications", name = "events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "business_id", nullable = false)
	private UUID businessId;
	@Column(name = "event_type", nullable = false, columnDefinition = "text")
	private EventType eventType;
	@Column(name = "source_id", nullable = false)
	private UUID sourceId;
	@Column(name = "invoice_id", nullable = false)
	private UUID invoiceId;
	@Column(nullable = false, columnDefinition = "text")
	private String data;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
