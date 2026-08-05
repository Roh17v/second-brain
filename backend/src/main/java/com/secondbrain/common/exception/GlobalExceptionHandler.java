package com.secondbrain.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
		return body(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
		return body(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
		return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
	public ResponseEntity<Map<String, Object>> handleAuthFailures(Exception ex) {
		return body(HttpStatus.UNAUTHORIZED, "Invalid email or password");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
		return body(HttpStatus.FORBIDDEN, "Access denied");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
		return body(HttpStatus.BAD_REQUEST, "Request body is missing or invalid JSON");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return body(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return body(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		return body(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
		return body(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(StorageException.class)
	public ResponseEntity<Map<String, Object>> handleStorage(StorageException ex) {
		log.error("Storage error", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
		log.error("Database error", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, rootMessage(ex));
	}

	/**
	 * Happens when a JSON error is returned for a request that only accepts
	 * {@code text/event-stream} (SSE chat). Force JSON so the handler can complete.
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public ResponseEntity<Map<String, Object>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
		log.debug("Not acceptable media type: {}", ex.getMessage());
		return body(HttpStatus.BAD_REQUEST, "Request failed (client Accept header cannot be satisfied for this error response)");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
		log.error("Unhandled error", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, rootMessage(ex));
	}

	private static String rootMessage(Throwable ex) {
		Throwable current = ex;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		String message = current.getMessage();
		if (message == null || message.isBlank()) {
			return current.getClass().getSimpleName();
		}
		return message;
	}

	/**
	 * Always force {@code application/json} so error bodies work even when the
	 * client Accept is only {@code text/event-stream} (streaming chat).
	 */
	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("timestamp", Instant.now().toString());
		payload.put("status", status.value());
		payload.put("error", status.getReasonPhrase());
		payload.put("message", message);
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload);
	}
}
