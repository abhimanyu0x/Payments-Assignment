package dev.dodo.common;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.dodo.platform.TraceIds;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class Errors extends ResponseEntityExceptionHandler {
	private static final PropertyNamingStrategies.NamingBase SNAKE_CASE = (PropertyNamingStrategies.NamingBase) PropertyNamingStrategies.SNAKE_CASE;
	private final ErrorWriter errors;
	private final TraceIds traceIds;

	@ExceptionHandler(ApiError.class)
	ResponseEntity<Object> business(ApiError e) {
		return errors.entity(e.getStatus(), e.getCode(), e.getMessage());
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<Object> database(DataAccessException e) {
		log.error("database_failure type={}", e.getClass().getSimpleName());
		return errors.entity(503, "temporarily_unavailable", Messages.SERVICE_BUSY);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> unexpected(Exception e) {
		log.error("unexpected_failure type={}", e.getClass().getSimpleName());
		return errors.entity(500, "internal_error", Messages.UNEXPECTED);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var details = e.getBindingResult().getFieldErrors().stream().map(Errors::invalidField).toList();
		return ResponseEntity.unprocessableEntity().body(ErrorResponse.of("validation_failed", Messages.DETAILS_INVALID, traceIds.current(), details));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (NestedExceptionUtils.getMostSpecificCause(e) instanceof StreamConstraintsException)
			return errors.entity(413, "request_too_large", Messages.REQUEST_TOO_LARGE);
		return errors.entity(400, "invalid_request", Messages.REQUEST_UNREADABLE);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var reply = switch (HttpStatus.valueOf(status.value())) {
			case BAD_REQUEST -> errors.body("invalid_request", e instanceof TypeMismatchException ? Messages.VALUE_INVALID : Messages.REQUEST_UNREADABLE);
			case NOT_FOUND -> errors.body("not_found", Messages.NOT_FOUND);
			case METHOD_NOT_ALLOWED -> errors.body("method_not_allowed", Messages.ACTION_NOT_ALLOWED);
			case UNSUPPORTED_MEDIA_TYPE -> errors.body("unsupported_media_type", Messages.FORMAT_NOT_SUPPORTED);
			case PAYLOAD_TOO_LARGE -> errors.body("request_too_large", Messages.REQUEST_TOO_LARGE);
			default -> status.is5xxServerError() ? errors.body("internal_error", Messages.UNEXPECTED) : errors.body("invalid_request", Messages.REQUEST_REJECTED);
		};
		return ResponseEntity.status(status).headers(headers).body(reply);
	}

	private static ErrorResponse.InvalidField invalidField(FieldError error) {
		String message = error.isBindingFailure() ? Messages.VALUE_INVALID : Objects.requireNonNullElse(error.getDefaultMessage(), Messages.VALUE_INVALID);
		return new ErrorResponse.InvalidField(SNAKE_CASE.translate(error.getField()), message);
	}
}
