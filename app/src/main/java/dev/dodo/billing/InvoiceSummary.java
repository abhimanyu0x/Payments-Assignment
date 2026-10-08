package dev.dodo.billing;

import dev.dodo.common.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummary(
	UUID id,
	UUID customerId,
	InvoiceState state,
	String currency,
	long totalAmountCents,
	String amountDisplay,
	LocalDate dueDate,
	Instant createdAt,
	Instant paidAt) {
	static InvoiceSummary from(InvoiceEntity invoice) {
		return new InvoiceSummary(
			invoice.getId(),
			invoice.getCustomerId(),
			invoice.getState(),
			invoice.getCurrency(),
			invoice.getTotalAmountCents(),
			Money.display(invoice.getTotalAmountCents()),
			invoice.getDueDate(),
			invoice.getCreatedAt(),
			invoice.getPaidAt());
	}
}
