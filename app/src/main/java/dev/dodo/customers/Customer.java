package dev.dodo.customers;

import java.time.Instant;
import java.util.UUID;

public record Customer(UUID id, String name, String email, Instant createdAt) {
	static Customer from(CustomerEntity customer) {
		return new Customer(customer.getId(), customer.getName(), customer.getEmail(), customer.getCreatedAt());
	}
}
