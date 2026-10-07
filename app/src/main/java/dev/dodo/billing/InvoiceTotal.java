package dev.dodo.billing;

import dev.dodo.http.ApiError;
import java.util.List;

public final class InvoiceTotal {
  private InvoiceTotal() {}

  public static long calculate(List<InvoiceInput.Item> items) {
    try {
      long total = 0;
      for (var item : items)
        total =
            Math.addExact(
                total, Math.multiplyExact(item.quantity().longValue(), item.unitAmountCents()));
      if (total < 1 || total > 1000000000000L)
        throw ApiError.invalid("Invoice total must be between 1 and 1000000000000 cents.");
      return total;
    } catch (ArithmeticException e) {
      throw ApiError.invalid("Invoice amount exceeds supported range.");
    }
  }
}
