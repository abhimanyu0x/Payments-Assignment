package dev.dodo.platform;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Converter
public abstract class WireValueConverter<E extends Enum<E> & WireValue> implements AttributeConverter<E, String> {
	private final Class<E> type;

	@Override
	public String convertToDatabaseColumn(E value) {
		return Optional.ofNullable(value).map(WireValue::value).orElse(null);
	}

	@Override
	public E convertToEntityAttribute(String value) {
		return Optional.ofNullable(value).map(text -> WireValue.from(type, text)).orElse(null);
	}
}
