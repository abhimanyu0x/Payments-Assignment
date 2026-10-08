package dev.dodo.payments;

import dev.dodo.platform.WireValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter extends WireValueConverter<PaymentStatus> {
	public PaymentStatusConverter() {
		super(PaymentStatus.class);
	}
}
