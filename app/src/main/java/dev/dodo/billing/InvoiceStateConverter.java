package dev.dodo.billing;

import dev.dodo.platform.WireValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class InvoiceStateConverter extends WireValueConverter<InvoiceState> {
	public InvoiceStateConverter() {
		super(InvoiceState.class);
	}
}
