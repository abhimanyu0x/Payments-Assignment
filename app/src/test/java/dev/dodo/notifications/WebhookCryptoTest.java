package dev.dodo.notifications;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebhookCryptoTest {
	@Test
	void secretRoundTripAndTamperDetection() {
		var cipher = new SecretCipher(new byte[32]);
		byte[] secret = cipher.generate();
		String a = cipher.encrypt(secret), b = cipher.encrypt(secret);
		assertNotEquals(a, b);
		assertArrayEquals(secret, cipher.decrypt(a));
		byte[] tampered = Base64.getDecoder().decode(a);
		tampered[tampered.length - 1] ^= 1;
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)));
	}

	@Test
	void rawBodyAndTimestampAreAuthenticated() {
		var signer = new WebhookSigner();
		byte[] key = "key".getBytes(StandardCharsets.UTF_8);
		String signed = signer.sign(key, 100, "{\"id\":1}");
		assertNotEquals(signed, signer.sign(key, 101, "{\"id\":1}"));
		assertNotEquals(signed, signer.sign(key, 100, "{ \"id\":1}"));
	}
}
