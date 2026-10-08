package dev.dodo.platform;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	public static UUID generate() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		long now = System.currentTimeMillis();
		for (int i = 0; i < 6; i++) bytes[i] = (byte) (now >>> (40 - 8 * i));
		bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
		bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
		var buffer = ByteBuffer.wrap(bytes);
		return new UUID(buffer.getLong(), buffer.getLong());
	}

	public static String hex() {
		return generate().toString().replace("-", "");
	}
}
