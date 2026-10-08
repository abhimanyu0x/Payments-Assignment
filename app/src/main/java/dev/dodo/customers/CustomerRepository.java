package dev.dodo.customers;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID>, QuerydslPredicateExecutor<CustomerEntity> {
	Optional<CustomerEntity> findByBusinessIdAndId(UUID businessId, UUID id);

	boolean existsByBusinessIdAndId(UUID businessId, UUID id);
}
