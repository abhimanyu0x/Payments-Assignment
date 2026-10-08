package dev.dodo.common;

import java.math.BigDecimal;

public final class Money {
	public static final String CURRENCY = "USD";

	private Money() {
	}

	public static String display(long cents) {
		return "$" + BigDecimal.valueOf(cents, 2).toPlainString();
	}
}
