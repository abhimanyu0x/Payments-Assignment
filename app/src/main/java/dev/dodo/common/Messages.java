package dev.dodo.common;

public final class Messages {
	public static final String API_KEY_REQUIRED = "Use a valid API key.";
	public static final String NO_ACCESS = "You do not have access.";
	public static final String REQUEST_REJECTED = "This request was rejected.";
	public static final String REQUEST_UNREADABLE = "We could not read this request.";
	public static final String REQUEST_TOO_LARGE = "This request is too large.";
	public static final String FORMAT_NOT_SUPPORTED = "This request format is not supported.";
	public static final String ACTION_NOT_ALLOWED = "This action is not allowed.";
	public static final String DETAILS_INVALID = "Some details are missing or not valid.";
	public static final String VALUE_INVALID = "One of the values is not valid.";
	public static final String PAGE_SIZE_INVALID = "Choose a page size between 1 and 100.";
	public static final String PAGE_LINK_EXPIRED = "This page link is no longer valid.";
	public static final String NOT_FOUND = "We could not find this item.";
	public static final String CUSTOMER_NOT_FOUND = "We could not find this customer.";
	public static final String SERVICE_BUSY = "The service is busy. Please try again.";
	public static final String UNEXPECTED = "Something went wrong. Please try again.";

	public static final String NAME_REQUIRED = "Enter a name.";
	public static final String NAME_TOO_LONG = "The name is too long.";
	public static final String EMAIL_REQUIRED = "Enter an email address.";
	public static final String EMAIL_INVALID = "Enter a valid email address.";
	public static final String EMAIL_TOO_LONG = "The email address is too long.";
	public static final String CUSTOMER_REQUIRED = "Choose a customer.";
	public static final String DUE_DATE_REQUIRED = "Choose a due date.";
	public static final String LINE_ITEMS_COUNT = "Add between 1 and 100 line items.";
	public static final String DESCRIPTION_REQUIRED = "Enter a description for each line item.";
	public static final String DESCRIPTION_TOO_LONG = "A description is too long.";
	public static final String QUANTITY_REQUIRED = "Enter a quantity for each line item.";
	public static final String QUANTITY_RANGE = "Quantity must be between 1 and 10000.";
	public static final String PRICE_REQUIRED = "Enter a price for each line item.";
	public static final String PRICE_NEGATIVE = "A price cannot be negative.";
	public static final String PRICE_TOO_HIGH = "A price is too high.";
	public static final String TOTAL_TOO_SMALL = "The invoice total must be more than zero.";
	public static final String TOTAL_TOO_LARGE = "The invoice total is too large.";
	public static final String PAYMENT_METHOD_REQUIRED = "Choose a payment method.";
	public static final String PAYMENT_METHOD_NOT_SUPPORTED = "This payment method is not supported.";
	public static final String WEBHOOK_ADDRESS_REQUIRED = "Enter a webhook address.";
	public static final String WEBHOOK_ADDRESS_TOO_LONG = "The webhook address is too long.";
	public static final String WEBHOOK_ADDRESS_NOT_ALLOWED = "Use a public HTTPS webhook address.";
	public static final String WEBHOOK_ADDRESS_EXISTS = "This webhook address is already registered.";

	public static final String PAYMENT_REFERENCE_REQUIRED = "This payment needs a unique reference.";
	public static final String ALREADY_PAID = "This invoice is already paid.";
	public static final String PAYMENT_IN_PROGRESS = "A payment for this invoice is still in progress.";
	public static final String REQUEST_REUSED = "This payment request was already used for another payment.";

	public static final String DOC_API = "Invoices and payments for each business. Amounts are whole US cents.";
	public static final String DOC_PAY = "Starts a payment and finishes it in the background. Check the payment status or wait for a webhook. Sending the same request again returns the same answer.";
	public static final String DOC_PAY_CONFLICT = "The invoice is paid, a payment is in progress, or this request was used before.";
	public static final String DOC_PAYMENT_REFERENCE = "A unique value for this payment. Send the same value if you retry.";

	private Messages() {
	}
}
