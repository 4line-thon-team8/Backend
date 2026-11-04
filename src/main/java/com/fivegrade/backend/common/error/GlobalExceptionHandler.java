package com.fivegrade.backend.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러에서 발생한 예외를 한곳에서 ErrorResponse(code, message)로 바꾼다.
 *
 * 원칙
 * - 성적 등 입력값은 응답 메시지와 로그에 넣지 않는다. 검증 실패 메시지에는 필드 이름까지만 쓴다.
 * - 500 응답은 고정 문구만 내려주고, 원인은 서버 로그에만 남긴다.
 * - 401/403 은 ErrorCode 만 정의해 두었다. 실제 연결은 인증(B-11) 방식이 정해진 뒤에 맞춘다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		log.warn("비즈니스 오류: {}", errorCode.getCode());
		return build(errorCode, e.getMessage());
	}

	/** 요청 본문(@Valid @RequestBody) 검증 실패 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
		Set<String> fields = new LinkedHashSet<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			fields.add(fieldError.getField());
		}
		return invalidRequest(fields);
	}

	/** @RequestParam, @PathVariable 등에 붙인 검증 실패 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
		Set<String> names = new LinkedHashSet<>();
		e.getParameterValidationResults().forEach(result -> {
			String name = result.getMethodParameter().getParameterName();
			if (name != null) {
				names.add(name);
			}
		});
		return invalidRequest(names);
	}

	/** @Validated 클래스 수준 검증 실패 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
		Set<String> names = new LinkedHashSet<>();
		for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
			String path = violation.getPropertyPath().toString();
			names.add(path.substring(path.lastIndexOf('.') + 1));
		}
		return invalidRequest(names);
	}

	/** JSON 형식이 깨졌거나 타입이 맞지 않는 본문. 원문 내용은 메시지에 넣지 않는다. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return build(ErrorCode.INVALID_REQUEST, "요청 본문 형식이 올바르지 않습니다.");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
		return invalidRequest(Set.of(e.getParameterName()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
		return invalidRequest(Set.of(e.getHeaderName()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return invalidRequest(Set.of(e.getName()));
	}

	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
		return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
		return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
	}

	/** 예상하지 못한 모든 예외. 응답에는 고정 문구만 쓰고 원인은 서버 로그에만 남긴다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
	}

	private ResponseEntity<ErrorResponse> invalidRequest(Set<String> fieldNames) {
		String message = ErrorCode.INVALID_REQUEST.getMessage();
		if (!fieldNames.isEmpty()) {
			message = "입력값이 올바르지 않습니다: " + String.join(", ", fieldNames);
		}
		return build(ErrorCode.INVALID_REQUEST, message);
	}

	private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, message));
	}

}
