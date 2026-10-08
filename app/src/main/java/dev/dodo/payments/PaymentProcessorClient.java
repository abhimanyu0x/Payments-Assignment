package dev.dodo.payments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.dodo.common.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class PaymentProcessorClient {
	public record Result(PaymentStatus status, String reference, String failure) {
		public static Result unknown(String reason) {
			return new Result(PaymentStatus.UNKNOWN, null, reason);
		}
	}

	record Charge(UUID operationId, long amountCents, String currency, String cardToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Reply(String status, String pspRef, String code) {
	}

	@Qualifier("pspClient")
	private final RestClient psp;
	private final PaymentAttemptRepository attempts;

	public Result execute(PaymentAttemptEntity claim) {
		try {
			if (claim.getClaimVersion() > 1) {
				var lookup = call(claim, psp.get().uri("/payments/{id}", claim.getId()));
				if (lookup.isPresent()) return lookup.get();
			}
			var charge = new Charge(claim.getId(), claim.getAmountCents(), Money.CURRENCY, claim.getMockCardToken());
			return call(claim, psp.post().uri("/payments").body(charge)).orElse(Result.unknown("psp_http_404"));
		} catch (Exception e) {
			return Result.unknown("psp_transport_error");
		}
	}

	private Optional<Result> call(PaymentAttemptEntity claim, RestClient.RequestHeadersSpec<?> request) {
		attempts.countCall(claim.getId(), claim.getClaimVersion());
		return request.exchange((sent, response) -> {
			int status = response.getStatusCode().value();
			if (status == 404) return Optional.empty();
			if (status != 200) return Optional.of(Result.unknown("psp_http_" + status));
			return Optional.of(interpret(response.bodyTo(Reply.class)));
		});
	}

	private static Result interpret(Reply reply) {
		if (Objects.isNull(reply)) return Result.unknown("invalid_psp_response");
		return switch (String.valueOf(reply.status())) {
			case "succeeded" -> StringUtils.hasText(reply.pspRef()) ? new Result(PaymentStatus.SUCCEEDED, reply.pspRef(), null) : Result.unknown("invalid_psp_response");
			case "failed" -> StringUtils.hasText(reply.code()) ? new Result(PaymentStatus.FAILED, null, reply.code()) : Result.unknown("invalid_psp_response");
			case "pending" -> Result.unknown("confirmation_pending");
			default -> Result.unknown("invalid_psp_response");
		};
	}
}
