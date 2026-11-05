package com.fivegrade.backend.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
 *   예외 메시지(getMessage)에는 입력 조각이 섞일 수 있어 로그에도 예외 클래스와 발생 위치만 남긴다.
 * - 5xx 응답은 어디서 던졌든 ErrorCode 의 고정 문구만 내려주고, 원인은 서버 로그에만 남긴다.
 * - 401/403 은 ErrorCode 만 정의해 두었다. 실제 연결은 인증(B-11) 방식이 정해진 뒤에 맞춘다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 로그에 남기는 스택 프레임 수. 원인 위치를 찾기에 충분한 만큼만 둔다. */
	private static final int STACK_FRAME_LIMIT = 8;

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		log.warn("비즈니스 오류: {}", errorCode.getCode());
		// 5xx 는 던진 쪽이 메시지를 넣었더라도 내부 정보가 나가지 않게 고정 문구만 쓴다.
		if (errorCode.getStatus().is5xxServerError()) {
			return build(errorCode, errorCode.getMessage());
		}
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
		log.warn("요청 본문을 읽을 수 없음: {}", e.getClass().getSimpleName());
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
		// 405 응답은 Allow 헤더로 허용되는 메서드를 알려야 하므로 예외가 가진 헤더를 그대로 싣는다.
		return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), e.getHeaders());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
		return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
	}

	/** 예상하지 못한 모든 예외. 응답에는 고정 문구만 쓰고 원인은 서버 로그에만 남긴다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외: {}", describe(e));
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
		return build(errorCode, message, HttpHeaders.EMPTY);
	}

	private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, HttpHeaders headers) {
		return ResponseEntity.status(errorCode.getStatus()).headers(headers).body(ErrorResponse.of(errorCode, message));
	}

	/**
	 * 로그용 예외 설명. getMessage 와 cause 메시지는 입력값을 담을 수 있어 쓰지 않는다.
	 * 원인 추적에 필요한 예외 클래스 체인과 상위 스택 프레임만 남긴다.
	 */
	private static String describe(Throwable e) {
		StringBuilder types = new StringBuilder(e.getClass().getName());
		for (Throwable cause = e.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
			types.append(" <- ").append(cause.getClass().getName());
		}
		String frames = Arrays.stream(e.getStackTrace())
				.limit(STACK_FRAME_LIMIT)
				.map(StackTraceElement::toString)
				.collect(Collectors.joining(" | "));
		return types + " @ " + frames;
	}

}
