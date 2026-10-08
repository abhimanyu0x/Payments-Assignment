package dev.dodo.billing;

import dev.dodo.common.ApiError;
import dev.dodo.common.Messages;
import java.util.List;

public final class InvoiceTotal {
	private InvoiceTotal() {
	}

	public static long calculate(List<InvoiceInput.LineItem> items) {
		try {
			long total = 0;
			for (var item : items)
				total =
					Math.addExact(
						total, Math.multiplyExact(item.quantity().longValue(), item.unitAmountCents()));
			if (total < 1 || total > 1000000000000L)
				throw ApiError.invalid(total < 1 ? Messages.TOTAL_TOO_SMALL : Messages.TOTAL_TOO_LARGE);
			return total;
		} catch (ArithmeticException e) {
			throw ApiError.invalid(Messages.TOTAL_TOO_LARGE);
		}
	}
}
