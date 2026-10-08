package dev.dodo.identity;

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
@Table(schema = "identity", name = "api_keys")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiKeyEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "business_id", nullable = false)
	private UUID businessId;
	@Column(name = "key_prefix", nullable = false, columnDefinition = "text")
	private String keyPrefix;
	@Column(name = "secret_hash", nullable = false, columnDefinition = "text")
	private String secretHash;
	@Column(name = "revoked_at")
	private Instant revokedAt;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	void revoke(Instant now) {
		revokedAt = now;
	}
}
