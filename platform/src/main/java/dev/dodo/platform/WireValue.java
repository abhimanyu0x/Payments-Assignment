package dev.dodo.platform;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

public interface WireValue {
	@JsonValue
	default String value() {
		return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
	}

	static <E extends Enum<E> & WireValue> E from(Class<E> type, String value) {
		return Arrays.stream(type.getEnumConstants())
			.filter(constant -> constant.value().equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value));
	}
}
