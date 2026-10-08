package dev.dodo.common;

import dev.dodo.configuration.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class RequestSizeFilter extends OncePerRequestFilter {
	private final AppProperties app;
	private final ErrorWriter errors;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		if (request.getContentLengthLong() > app.maxRequestSize().toBytes()) {
			errors.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "request_too_large", Messages.REQUEST_TOO_LARGE);
			return;
		}
		chain.doFilter(request, response);
	}
}
