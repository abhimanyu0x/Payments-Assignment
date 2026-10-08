package dev.dodo.notifications;

import dev.dodo.platform.WireValue;

public enum DeliveryStatus implements WireValue {
	PENDING,
	DELIVERED,
	EXHAUSTED
}
