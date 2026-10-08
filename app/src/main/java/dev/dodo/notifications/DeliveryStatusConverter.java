package dev.dodo.notifications;

import dev.dodo.platform.WireValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DeliveryStatusConverter extends WireValueConverter<DeliveryStatus> {
	public DeliveryStatusConverter() {
		super(DeliveryStatus.class);
	}
}
