package dev.dodo.billing;

import dev.dodo.common.Messages;
import dev.dodo.platform.WireValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentBlock implements WireValue {
	INVOICE_ALREADY_PAID(Messages.ALREADY_PAID),
	PAYMENT_IN_PROGRESS(Messages.PAYMENT_IN_PROGRESS);

	private final String message;

	static PaymentBlock of(InvoiceState state, boolean unresolved) {
		if (state == InvoiceState.PAID) return INVOICE_ALREADY_PAID;
		return unresolved ? PAYMENT_IN_PROGRESS : null;
	}
}
