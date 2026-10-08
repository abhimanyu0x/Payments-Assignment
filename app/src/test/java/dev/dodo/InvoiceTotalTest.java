package dev.dodo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dodo.billing.InvoiceInput;
import dev.dodo.billing.InvoiceTotal;
import dev.dodo.common.ApiError;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTotalTest {
	@Test
	void centsRemainExact() {
		assertEquals(3897, InvoiceTotal.calculate(List.of(new InvoiceInput.LineItem("Work", 3, 1299L))));
	}

	@Test
	void totalBoundRejectsLargeValidItems() {
		assertThrows(ApiError.class, () -> InvoiceTotal.calculate(List.of(new InvoiceInput.LineItem("Work", 10000, 1000000000000L))));
	}

	@Test
	void zeroInvoiceIsRejected() {
		assertThrows(ApiError.class, () -> InvoiceTotal.calculate(List.of(new InvoiceInput.LineItem("Free", 1, 0L))));
	}
}
