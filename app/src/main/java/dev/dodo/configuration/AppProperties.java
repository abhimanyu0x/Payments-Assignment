package dev.dodo.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
	@NotBlank String pspUrl,
	@NotNull Duration pspTimeout,
	@NotBlank String webhookUrl,
	@NotNull Duration webhookTimeout,
	@NotBlank String encryptionKey,
	boolean demoSeed,
	String demoWebhookSecret,
	boolean workersEnabled,
	@NotNull Duration workerPollInterval,
	@Min(1) int workerConcurrency,
	@NotNull Duration paymentLease,
	@NotNull Duration paymentRecovery,
	@NotEmpty List<Duration> paymentRetryDelays,
	@Min(1) int webhookMaxAttempts,
	@NotNull Duration webhookLease,
	@NotNull Duration webhookDeliveryBudget,
	@NotEmpty List<Duration> webhookRetryDelays,
	@NotNull DataSize maxRequestSize) {
	public Duration paymentRetryDelay(int round) {
		return paymentRetryDelays.get(Math.min(round, paymentRetryDelays.size() - 1));
	}

	public Duration webhookRetryDelay(int attempt) {
		return webhookRetryDelays.get(Math.clamp(attempt - 1, 0, webhookRetryDelays.size() - 1));
	}

	public byte[] encryptionKeyBytes() {
		return Base64.getDecoder().decode(encryptionKey);
	}

	public byte[] demoWebhookSecretBytes() {
		return Base64.getDecoder().decode(demoWebhookSecret);
	}

	@AssertTrue(message = "app.encryption-key must be base64 of 32 bytes")
	public boolean isEncryptionKeyValid() {
		return isBase64Of32Bytes(encryptionKey);
	}

	@AssertTrue(message = "app.demo-webhook-secret must be base64 of 32 bytes when app.demo-seed is true")
	public boolean isDemoWebhookSecretValid() {
		return !demoSeed || isBase64Of32Bytes(demoWebhookSecret);
	}

	private static boolean isBase64Of32Bytes(String value) {
		try {
			return StringUtils.hasText(value) && Base64.getDecoder().decode(value).length == 32;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
