package com.fiscuvia.platform;

import java.util.UUID;
import java.util.regex.Pattern;

public final class ApiIdentifiers {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ApiIdentifiers() {}

    public static UUID uuid(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new ApiFailure(ApiErrorCode.BAD_REQUEST);
        }
        return UUID.fromString(value);
    }

    /** Syntax only. Durable replay/scope/fingerprint enforcement belongs to each future mutation. */
    public static UUID idempotencyKey(String value) { return uuid(value); }
}
