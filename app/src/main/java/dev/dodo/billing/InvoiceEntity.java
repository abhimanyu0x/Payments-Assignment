package dev.dodo.billing;

import dev.dodo.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.Assert;

@Entity
@Table(schema = "billing", name = "invoices")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "business_id", nullable = false)
	private UUID businessId;
	@Column(name = "customer_id", nullable = false)
	private UUID customerId;
	@Builder.Default
	@Column(nullable = false, columnDefinition = "text")
	private InvoiceState state = InvoiceState.OPEN;
	@Builder.Default
	@Column(nullable = false, columnDefinition = "text")
	private String currency = Money.CURRENCY;
	@Column(name = "total_amount_cents", nullable = false)
	private long totalAmountCents;
	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;
	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "paid_at")
	private Instant paidAt;

	void markPaid(Instant now) {
		Assert.state(state == InvoiceState.OPEN, "Only an open invoice can be paid");
		state = InvoiceState.PAID;
		paidAt = now;
	}
}
