package dev.dodo.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "billing", name = "invoice_items")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceItemEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private UUID id;
	@Column(name = "invoice_id", nullable = false)
	private UUID invoiceId;
	@Column(nullable = false)
	private int position;
	@Column(nullable = false, columnDefinition = "text")
	private String description;
	@Column(nullable = false)
	private int quantity;
	@Column(name = "unit_amount_cents", nullable = false)
	private long unitAmountCents;
}
