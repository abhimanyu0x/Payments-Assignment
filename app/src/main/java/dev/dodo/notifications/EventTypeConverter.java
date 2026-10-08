package dev.dodo.notifications;

import dev.dodo.platform.WireValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EventTypeConverter extends WireValueConverter<EventType> {
	public EventTypeConverter() {
		super(EventType.class);
	}
}
