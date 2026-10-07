package dev.dodo.billing;

import java.util.*;

public interface InvoicePayments {
  Map<String, Object> lock(UUID business, UUID invoice);

  void requirePayable(Map<String, Object> invoice, boolean unresolved);

  void requireExists(UUID business, UUID invoice);

  void markPaid(UUID business, UUID invoice);
}
