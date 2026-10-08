package dev.dodo.billing;

import dev.dodo.common.Messages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceInput(
	@NotNull(message = Messages.CUSTOMER_REQUIRED) UUID customerId,
	@NotNull(message = Messages.DUE_DATE_REQUIRED) LocalDate dueDate,
	@NotEmpty(message = Messages.LINE_ITEMS_COUNT) @Size(min = 1, max = 100, message = Messages.LINE_ITEMS_COUNT) List<@NotNull(message = Messages.LINE_ITEMS_COUNT) @Valid LineItem> items) {
	public record LineItem(
		@NotBlank(message = Messages.DESCRIPTION_REQUIRED) @Size(max = 500, message = Messages.DESCRIPTION_TOO_LONG) String description,
		@NotNull(message = Messages.QUANTITY_REQUIRED) @Min(value = 1, message = Messages.QUANTITY_RANGE) @Max(value = 10000, message = Messages.QUANTITY_RANGE) Integer quantity,
		@NotNull(message = Messages.PRICE_REQUIRED) @Min(value = 0, message = Messages.PRICE_NEGATIVE) @Max(value = 1000000000000L, message = Messages.PRICE_TOO_HIGH) Long unitAmountCents) {
	}
}
