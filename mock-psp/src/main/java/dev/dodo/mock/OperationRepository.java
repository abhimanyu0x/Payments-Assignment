package dev.dodo.mock;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface OperationRepository extends JpaRepository<OperationEntity, UUID> {
	String SKIP_LOCKED = "-2";

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OperationEntity> findForUpdateByOperationId(UUID operationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
	List<OperationEntity> findByStatusAndCompleteAfterLessThanEqual(OperationStatus status, Instant now);
}
