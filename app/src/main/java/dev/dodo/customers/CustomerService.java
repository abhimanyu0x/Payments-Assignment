package dev.dodo.customers;

import dev.dodo.common.ApiError;
import dev.dodo.common.Messages;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import dev.dodo.common.Pages;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {
	private static final QCustomerEntity CUSTOMER = QCustomerEntity.customerEntity;
	private final CustomerRepository customers;
	private final Pages pages;

	@Transactional
	public Customer create(UUID business, String name, String email) {
		var customer = CustomerEntity.builder().businessId(business).name(name.strip()).email(email.strip()).build();
		return Customer.from(customers.save(customer));
	}

	public Customer get(UUID business, UUID id) {
		return customers.findByBusinessIdAndId(business, id).map(Customer::from).orElseThrow(ApiError::missing);
	}

	public Page<Customer> list(UUID business, PageQuery page) {
		return pages.list(customers, CUSTOMER.businessId.eq(business), business, "customers", page, Customer::from);
	}

	public void require(UUID business, UUID id) {
		if (!customers.existsByBusinessIdAndId(business, id)) throw new ApiError(404, "not_found", Messages.CUSTOMER_NOT_FOUND);
	}
}
