package dev.dodo.billing;

import dev.dodo.common.ApiError;
import dev.dodo.common.Money;
import dev.dodo.common.Page;
import dev.dodo.common.PageQuery;
import dev.dodo.common.Pages;
import dev.dodo.customers.CustomerService;
import dev.dodo.notifications.EventType;
import dev.dodo.notifications.WebhookEvents;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService implements InvoicePayments {
	private static final QInvoiceEntity INVOICE = QInvoiceEntity.invoiceEntity;
	private final InvoiceRepository invoices;
	private final InvoiceItemRepository items;
	private final CustomerService customers;
	private final WebhookEvents events;
	private final PaymentActivity payments;
	private final Pages pages;
	private final Clock clock;

	public InvoicePreview preview(UUID business, InvoiceInput input) {
		customers.require(business, input.customerId());
		return new InvoicePreview(Money.CURRENCY, InvoiceTotal.calculate(input.items()));
	}

	@Transactional
	public Invoice create(UUID business, InvoiceInput input) {
		customers.require(business, input.customerId());
		long total = InvoiceTotal.calculate(input.items());
		var invoice = invoices.save(InvoiceEntity.builder().businessId(business).customerId(input.customerId()).totalAmountCents(total).dueDate(input.dueDate()).build());
		var lines = input.items();
		items.saveAll(IntStream.range(0, lines.size())
			.mapToObj(position -> InvoiceItemEntity.builder().invoiceId(invoice.getId()).position(position).description(lines.get(position).description().strip()).quantity(lines.get(position).quantity()).unitAmountCents(lines.get(position).unitAmountCents()).build())
			.toList());
		events.record(business, EventType.INVOICE_CREATED, invoice.getId(), invoice.getId(), InvoiceEventData.created(invoice.getId(), total));
		return get(business, invoice.getId());
	}

	public Invoice get(UUID business, UUID id) {
		var invoice = invoices.findByBusinessIdAndId(business, id).orElseThrow(ApiError::missing);
		var lines = items.findByInvoiceIdOrderByPositionAsc(id).stream()
			.map(item -> new Invoice.InvoiceItem(item.getDescription(), item.getQuantity(), item.getUnitAmountCents(), Money.display(item.getUnitAmountCents())))
			.toList();
		return Invoice.of(InvoiceSummary.from(invoice), lines, PaymentBlock.of(invoice.getState(), payments.unresolved(id)));
	}

	public Page<InvoiceSummary> list(UUID business, InvoiceState state, PageQuery page) {
		var where = INVOICE.businessId.eq(business).and(Optional.ofNullable(state).map(INVOICE.state::eq).orElse(null));
		String list = "invoices:" + Optional.ofNullable(state).map(InvoiceState::value).orElse("all");
		return pages.list(invoices, where, business, list, page, InvoiceSummary::from);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public PayableInvoice lock(UUID business, UUID invoice) {
		var locked = invoices.findForUpdateByBusinessIdAndId(business, invoice).orElseThrow(ApiError::missing);
		return new PayableInvoice(locked.getId(), locked.getState(), locked.getTotalAmountCents());
	}

	@Override
	public void requirePayable(PayableInvoice invoice, boolean unresolved) {
		var block = PaymentBlock.of(invoice.state(), unresolved);
		if (Objects.nonNull(block)) throw ApiError.conflict(block.value(), block.getMessage());
	}

	@Override
	public void requireExists(UUID business, UUID invoice) {
		if (!invoices.existsByBusinessIdAndId(business, invoice)) throw ApiError.missing();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void markPaid(UUID business, UUID invoice) {
		invoices.findById(invoice).filter(found -> found.getBusinessId().equals(business)).orElseThrow(ApiError::missing).markPaid(Instant.now(clock));
	}
}
