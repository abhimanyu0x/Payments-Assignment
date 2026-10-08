package dev.dodo.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItemEntity, UUID> {
	List<InvoiceItemEntity> findByInvoiceIdOrderByPositionAsc(UUID invoiceId);
}
