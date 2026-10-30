package com.fiscuvia.platform;

import java.util.Objects;

/** Explicit infrastructure failure for adapters; domain modules map their own failures at their HTTP boundary. */
public final class ApiFailure extends RuntimeException {
    private final ApiErrorCode code;

    public ApiFailure(ApiErrorCode code) {
        super(Objects.requireNonNull(code).name());
        this.code = code;
    }

    public ApiErrorCode code() { return code; }
}
