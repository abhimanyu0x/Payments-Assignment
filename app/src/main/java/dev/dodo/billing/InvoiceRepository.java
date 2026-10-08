package dev.dodo.billing;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID>, QuerydslPredicateExecutor<InvoiceEntity> {
	Optional<InvoiceEntity> findByBusinessIdAndId(UUID businessId, UUID id);

	boolean existsByBusinessIdAndId(UUID businessId, UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<InvoiceEntity> findForUpdateByBusinessIdAndId(UUID businessId, UUID id);
}
