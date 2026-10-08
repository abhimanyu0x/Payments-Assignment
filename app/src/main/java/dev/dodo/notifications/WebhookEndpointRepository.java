package dev.dodo.notifications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, UUID>, QuerydslPredicateExecutor<WebhookEndpointEntity> {
	Optional<WebhookEndpointEntity> findByBusinessIdAndId(UUID businessId, UUID id);

	boolean existsByBusinessIdAndUrlAndActiveTrue(UUID businessId, String url);

	List<WebhookEndpointEntity> findByBusinessIdAndActiveTrue(UUID businessId);
}
