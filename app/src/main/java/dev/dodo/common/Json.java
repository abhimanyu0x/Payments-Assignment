package dev.dodo.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Json {
	private final ObjectMapper mapper;

	public String write(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot serialize response", e);
		}
	}

	public <T> T read(String value, Class<T> type) {
		try {
			return mapper.readValue(value, type);
		} catch (Exception e) {
			throw new IllegalStateException("Invalid stored JSON", e);
		}
	}

}
