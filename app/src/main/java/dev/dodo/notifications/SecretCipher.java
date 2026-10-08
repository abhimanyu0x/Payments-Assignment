package dev.dodo.notifications;

import dev.dodo.configuration.AppProperties;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {
	private static final BytesKeyGenerator SECRETS = KeyGenerators.secureRandom(32);
	private final BytesEncryptor encryptor;

	@Autowired
	SecretCipher(AppProperties app) {
		this(app.encryptionKeyBytes());
	}

	SecretCipher(byte[] key) {
		encryptor = new AesBytesEncryptor(new SecretKeySpec(key, "AES"), KeyGenerators.secureRandom(12), AesBytesEncryptor.CipherAlgorithm.GCM);
	}

	public byte[] generate() {
		return SECRETS.generateKey();
	}

	public String encrypt(byte[] secret) {
		return Base64.getEncoder().encodeToString(encryptor.encrypt(secret));
	}

	public byte[] decrypt(String encoded) {
		return encryptor.decrypt(Base64.getDecoder().decode(encoded));
	}
}
