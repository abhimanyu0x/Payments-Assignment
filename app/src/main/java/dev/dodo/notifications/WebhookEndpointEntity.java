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
@Table(schema = "notifications", name = "webhook_endpoints")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WebhookEndpointEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "business_id", nullable = false)
	private UUID businessId;
	@Column(nullable = false, columnDefinition = "text")
	private String url;
	@Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
	private String secretCiphertext;
	@Builder.Default
	@Column(nullable = false)
	private boolean active = true;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
