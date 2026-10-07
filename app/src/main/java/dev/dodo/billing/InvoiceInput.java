package dev.dodo.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public record InvoiceInput(
    @NotNull UUID customerId,
    @NotNull LocalDate dueDate,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {
  public record Item(
      @NotBlank @Size(max = 500) String description,
      @NotNull @Min(1) @Max(10000) Integer quantity,
      @NotNull @Min(0) @Max(1000000000000L) Long unitAmountCents) {}
}
