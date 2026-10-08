package dev.dodo.common;

import dev.dodo.platform.WireValue;
import java.beans.PropertyEditorSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class WireValueEditor<E extends Enum<E> & WireValue> extends PropertyEditorSupport {
	private final Class<E> type;

	@Override
	public void setAsText(String text) {
		setValue(StringUtils.hasText(text) ? WireValue.from(type, text) : null);
	}
}
