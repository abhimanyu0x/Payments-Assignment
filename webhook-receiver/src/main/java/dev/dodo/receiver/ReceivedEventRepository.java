package dev.dodo.receiver;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivedEventRepository extends JpaRepository<ReceivedEventEntity, UUID> {
	List<ReceivedEventEntity> findTop100ByOrderByReceivedAtDesc();
}
