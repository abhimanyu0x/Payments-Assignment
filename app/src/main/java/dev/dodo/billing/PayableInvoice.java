package dev.dodo.billing;

import java.util.UUID;

public record PayableInvoice(UUID id, InvoiceState state, long totalAmountCents) {
}
