package dev.dodo.receiver;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SignatureVerifier {
	private static final String ALGORITHM = "HmacSHA256";
	private final ReceiverProperties properties;
	private final Clock clock;

	public boolean verify(byte[] body, String timestamp, String signature) {
		if (ObjectUtils.isEmpty(body) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) return false;
		long sentAt;
		try {
			sentAt = Long.parseLong(timestamp);
		} catch (NumberFormatException e) {
			return false;
		}
		var age = Duration.between(Instant.ofEpochSecond(sentAt), Instant.now(clock)).abs();
		if (age.compareTo(properties.tolerance()) > 0) return false;
		byte[] expected = ("v1=" + sign(timestamp, body)).getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
	}

	String sign(String timestamp, byte[] body) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(properties.secretBytes(), ALGORITHM));
			mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
			return HexFormat.of().formatHex(mac.doFinal(body));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Cannot compute signature", e);
		}
	}
}
