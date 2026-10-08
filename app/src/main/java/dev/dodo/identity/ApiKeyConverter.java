package dev.dodo.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

class ApiKeyConverter implements AuthenticationConverter {
	private static final String BEARER = "Bearer ";
	private static final int MAX_HEADER_LENGTH = 256;
	private static final int MAX_PREFIX_LENGTH = 64;
	private static final int MAX_SECRET_LENGTH = 128;

	@Override
	public Authentication convert(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(header)) return null;
		if (header.length() > MAX_HEADER_LENGTH || !header.startsWith(BEARER)) throw new BadCredentialsException("Malformed authorization header");
		String[] key = header.substring(BEARER.length()).split("\\.", 2);
		boolean wellFormed = key.length == 2
			&& StringUtils.hasText(key[0]) && key[0].length() <= MAX_PREFIX_LENGTH
			&& StringUtils.hasText(key[1]) && key[1].length() <= MAX_SECRET_LENGTH;
		if (!wellFormed) throw new BadCredentialsException("Malformed API key");
		return UsernamePasswordAuthenticationToken.unauthenticated(key[0], key[1]);
	}
}
