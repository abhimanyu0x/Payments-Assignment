package dev.dodo.billing;

import java.util.UUID;

public interface InvoicePayments {
	PayableInvoice lock(UUID business, UUID invoice);

	void requirePayable(PayableInvoice invoice, boolean unresolved);

	void requireExists(UUID business, UUID invoice);

	void markPaid(UUID business, UUID invoice);
}
