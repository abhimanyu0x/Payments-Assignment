package dev.dodo.notifications;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.dodo.platform.WireValue;
import java.util.Locale;

public enum EventType implements WireValue {
	INVOICE_CREATED,
	INVOICE_PAID,
	INVOICE_PAYMENT_FAILED;

	@JsonValue
	@Override
	public String value() {
		return name().toLowerCase(Locale.ROOT).replaceFirst("_", ".");
	}
}
