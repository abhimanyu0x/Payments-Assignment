package dev.dodo.mock;

import dev.dodo.platform.WireValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OperationStatusConverter extends WireValueConverter<OperationStatus> {
	public OperationStatusConverter() {
		super(OperationStatus.class);
	}
}
