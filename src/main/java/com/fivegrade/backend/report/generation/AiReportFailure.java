package com.fivegrade.backend.report.generation;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** 외부 오류 본문·원인 예외를 보관하지 않고 고정 코드만 전달한다. */
public final class AiReportFailure extends IllegalStateException {

    public enum Reason {
        AUTH_ERROR,
        RATE_OR_QUOTA_LIMIT,
        PROVIDER_ERROR,
        HTTP_ERROR,
        TIMEOUT,
        NETWORK_ERROR,
        INVALID_RESPONSE,
        RESPONSE_TOO_LARGE,
        CAPACITY_LIMIT,
        INTERRUPTED,
        UNAVAILABLE,
        VALIDATION_REJECTED,
        SOURCE_TOO_LARGE
    }

    private final Reason reason;

    public AiReportFailure(Reason reason) {
        super(Objects.requireNonNull(reason).name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static Reason forHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return Reason.AUTH_ERROR;
        }

        if (status == 429) {
            return Reason.RATE_OR_QUOTA_LIMIT;
        }

        if (status == 408 || status == 504) {
            return Reason.TIMEOUT;
        }

        return status >= 500
                ? Reason.PROVIDER_ERROR
                : Reason.HTTP_ERROR;
    }

    public static Reason reasonOf(Throwable failure) {
        Throwable current = failure;

        while ((current instanceof ExecutionException
                || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }

        if (current instanceof AiReportFailure typed) {
            return typed.reason();
        }

        if (current instanceof InterruptedException) {
            return Reason.INTERRUPTED;
        }

        if (current instanceof TimeoutException
                || current instanceof HttpTimeoutException) {
            return Reason.TIMEOUT;
        }

        return current instanceof IOException
                ? Reason.NETWORK_ERROR
                : Reason.UNAVAILABLE;
    }
}
