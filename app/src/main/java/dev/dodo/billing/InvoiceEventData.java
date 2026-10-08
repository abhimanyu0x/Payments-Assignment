package dev.dodo.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.dodo.common.Money;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceEventData(UUID invoiceId, UUID paymentAttemptId, InvoiceState state, long totalAmountCents, String currency, String failureCode) {
	public static InvoiceEventData created(UUID invoice, long total) {
		return new InvoiceEventData(invoice, null, InvoiceState.OPEN, total, Money.CURRENCY, null);
	}

	public static InvoiceEventData paid(UUID invoice, UUID attempt, long total) {
		return new InvoiceEventData(invoice, attempt, InvoiceState.PAID, total, Money.CURRENCY, null);
	}

	public static InvoiceEventData failed(UUID invoice, UUID attempt, long total, String failure) {
		return new InvoiceEventData(invoice, attempt, InvoiceState.OPEN, total, Money.CURRENCY, failure);
	}
}
