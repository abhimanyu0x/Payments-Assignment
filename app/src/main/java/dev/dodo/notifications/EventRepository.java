package dev.dodo.notifications;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface EventRepository extends JpaRepository<EventEntity, UUID>, QuerydslPredicateExecutor<EventEntity> {
	boolean existsByBusinessIdAndEventTypeAndSourceId(UUID businessId, EventType eventType, UUID sourceId);
}
