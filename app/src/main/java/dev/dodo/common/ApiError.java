package dev.dodo.common;

import lombok.Getter;

@Getter
public class ApiError extends RuntimeException {
	private final int status;
	private final String code;

	public ApiError(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static ApiError missing() {
		return new ApiError(404, "not_found", Messages.NOT_FOUND);
	}

	public static ApiError invalid(String message) {
		return new ApiError(422, "validation_failed", message);
	}

	public static ApiError conflict(String code, String message) {
		return new ApiError(409, code, message);
	}
}
