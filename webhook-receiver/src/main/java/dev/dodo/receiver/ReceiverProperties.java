package dev.dodo.receiver;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("receiver")
public record ReceiverProperties(String webhookSecret, @NotNull Duration tolerance, @NotNull DataSize maxBodySize) {
	public byte[] secretBytes() {
		return Base64.getDecoder().decode(webhookSecret);
	}

	@AssertTrue(message = "receiver.webhook-secret must be base64 of 32 bytes")
	public boolean isWebhookSecretValid() {
		try {
			return StringUtils.hasText(webhookSecret) && secretBytes().length == 32;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
