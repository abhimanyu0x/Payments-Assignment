package dev.dodo.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;

public record PageQuery(
	@Schema(defaultValue = "20") @Min(value = 1, message = Messages.PAGE_SIZE_INVALID) @Max(value = 100, message = Messages.PAGE_SIZE_INVALID) Integer limit,
	String cursor) {
	public PageQuery {
		limit = Objects.requireNonNullElse(limit, 20);
	}
}
