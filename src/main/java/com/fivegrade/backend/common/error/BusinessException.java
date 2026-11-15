package com.fivegrade.backend.common.error;

/**
 * 서비스가 "의도한 오류"를 알릴 때 던지는 예외.
 * GlobalExceptionHandler 가 ErrorCode 의 상태·코드로 응답을 만든다.
 * 메시지에 성적 등 입력값을 넣지 않는다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

}
