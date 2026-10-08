package dev.dodo.payments;

import dev.dodo.platform.WireValue;
import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus implements WireValue {
	PENDING,
	UNKNOWN,
	SUCCEEDED,
	FAILED;

	public static final Set<PaymentStatus> UNRESOLVED = EnumSet.of(PENDING, UNKNOWN);
}
