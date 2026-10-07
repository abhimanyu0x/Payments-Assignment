package dev.dodo;

import static org.junit.jupiter.api.Assertions.*;

import dev.dodo.billing.*;
import dev.dodo.http.ApiError;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTotalTest {
  @Test
  void centsRemainExact() {
    assertEquals(3897, InvoiceTotal.calculate(List.of(new InvoiceInput.Item("Work", 3, 1299L))));
  }

  @Test
  void totalBoundRejectsLargeValidItems() {
    assertThrows(
        ApiError.class,
        () ->
            InvoiceTotal.calculate(List.of(new InvoiceInput.Item("Work", 10000, 1000000000000L))));
  }

  @Test
  void zeroInvoiceIsRejected() {
    assertThrows(
        ApiError.class,
        () -> InvoiceTotal.calculate(List.of(new InvoiceInput.Item("Free", 1, 0L))));
  }
}
