package dev.dodo.identity;

import dev.dodo.platform.Sha256;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
	public record IssuedKey(UUID id, String apiKey) {
	}

	private static final BytesKeyGenerator PREFIXES = KeyGenerators.secureRandom(12);
	private static final BytesKeyGenerator SECRETS = KeyGenerators.secureRandom(32);
	private final ApiKeyRepository keys;
	private final Clock clock;

	@Transactional
	public IssuedKey issue(UUID business) {
		String prefix = encode(PREFIXES.generateKey());
		String secret = encode(SECRETS.generateKey());
		ApiKeyEntity key;
		try {
			key = keys.saveAndFlush(ApiKeyEntity.builder().businessId(business).keyPrefix(prefix).secretHash(Sha256.hex(secret)).build());
		} catch (DataIntegrityViolationException e) {
			throw new IllegalArgumentException("Unknown business " + business, e);
		}
		return new IssuedKey(key.getId(), prefix + "." + secret);
	}

	@Transactional
	public boolean revoke(UUID business, UUID key) {
		return keys.findByIdAndBusinessIdAndRevokedAtIsNull(key, business).map(found -> {
			found.revoke(Instant.now(clock));
			return true;
		}).orElse(false);
	}

	private static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}
}
