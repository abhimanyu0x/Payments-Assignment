package dev.dodo.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class TraceIdResponseFilter extends OncePerRequestFilter {
	public static final String HEADER = "X-Request-Id";
	public static final String ATTRIBUTE = "traceId";
	private final TraceIds traceIds;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		Optional.ofNullable(traceIds.current()).ifPresent(id -> {
			response.setHeader(HEADER, id);
			request.setAttribute(ATTRIBUTE, id);
		});
		chain.doFilter(request, response);
	}
}
