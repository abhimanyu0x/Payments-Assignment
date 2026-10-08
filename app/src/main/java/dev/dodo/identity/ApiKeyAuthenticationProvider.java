package dev.dodo.identity;

import dev.dodo.platform.Sha256;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {
	static final String BUSINESS_ROLE = "BUSINESS";
	private static final byte[] UNKNOWN_KEY_HASH = bytes(Sha256.hex("unknown-key"));
	private final ApiKeyRepository keys;

	@Override
	public Authentication authenticate(Authentication authentication) {
		String prefix = String.valueOf(authentication.getPrincipal());
		String secret = String.valueOf(authentication.getCredentials());
		try {
			var found = keys.findByKeyPrefixAndRevokedAtIsNull(prefix);
			byte[] expected = found.map(key -> bytes(key.getSecretHash())).orElse(UNKNOWN_KEY_HASH);
			if (!MessageDigest.isEqual(expected, bytes(Sha256.hex(secret))) || found.isEmpty()) throw new BadCredentialsException("Unknown API key");
			return UsernamePasswordAuthenticationToken.authenticated(found.get().getBusinessId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + BUSINESS_ROLE)));
		} catch (DataAccessException e) {
			throw new AuthenticationServiceException("API key lookup failed", e);
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

	private static byte[] bytes(String hash) {
		return hash.getBytes(StandardCharsets.US_ASCII);
	}
}
