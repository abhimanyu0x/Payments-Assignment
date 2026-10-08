package dev.dodo.billing;

import java.util.UUID;

public interface PaymentActivity {
	boolean unresolved(UUID invoice);
}
