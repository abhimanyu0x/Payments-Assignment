package dev.dodo.common;

import dev.dodo.platform.TraceIds;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ErrorWriter {
	private final TraceIds traceIds;
	private final Json json;

	public ErrorResponse body(String code, String message) {
		return ErrorResponse.of(code, message, traceIds.current());
	}

	public ResponseEntity<Object> entity(int status, String code, String message) {
		return ResponseEntity.status(status).body(body(code, message));
	}

	public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		if (status == HttpServletResponse.SC_UNAUTHORIZED) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(json.write(body(code, message)));
	}
}
