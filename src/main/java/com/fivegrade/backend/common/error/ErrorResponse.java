package com.fivegrade.backend.common.error;

/**
 * 모든 오류 응답의 모양. 필드는 code, message 두 개만 둔다.
 */
public record ErrorResponse(String code, String message) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.getCode(), message);
	}

}
