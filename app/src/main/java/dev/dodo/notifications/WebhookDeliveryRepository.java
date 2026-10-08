package dev.dodo.notifications;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID>, QuerydslPredicateExecutor<WebhookDeliveryEntity>, WebhookDeliveryClaims {
}
