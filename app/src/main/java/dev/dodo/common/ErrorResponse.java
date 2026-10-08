package dev.dodo.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorResponse(ErrorDetails error) {
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorDetails(String code, String message, String requestId, List<InvalidField> details) {
	}

	public record InvalidField(String field, String message) {
	}

	public static ErrorResponse of(String code, String message, String requestId) {
		return of(code, message, requestId, null);
	}

	public static ErrorResponse of(String code, String message, String requestId, List<InvalidField> details) {
		return new ErrorResponse(new ErrorDetails(code, message, requestId, details));
	}
}
