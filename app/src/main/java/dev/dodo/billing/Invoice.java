package dev.dodo.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Invoice(
	UUID id,
	UUID customerId,
	InvoiceState state,
	String currency,
	long totalAmountCents,
	String amountDisplay,
	LocalDate dueDate,
	Instant createdAt,
	Instant paidAt,
	List<InvoiceItem> items,
	List<String> allowedActions,
	PaymentBlock paymentBlockReason) {
	public record InvoiceItem(String description, int quantity, long unitAmountCents, String unitAmountDisplay) {
	}

	static Invoice of(InvoiceSummary s, List<InvoiceItem> items, PaymentBlock block) {
		return new Invoice(s.id(), s.customerId(), s.state(), s.currency(), s.totalAmountCents(), s.amountDisplay(), s.dueDate(), s.createdAt(), s.paidAt(), items, Objects.isNull(block) ? List.of("pay") : List.of(), block);
	}
}
