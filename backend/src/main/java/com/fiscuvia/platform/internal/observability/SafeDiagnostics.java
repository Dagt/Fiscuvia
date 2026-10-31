package com.fiscuvia.platform.internal.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;

public final class SafeDiagnostics {
    public enum Failure { CONFIGURATION_REJECTED, DATABASE_AUTHENTICATION, DATABASE_UNAVAILABLE,
        DATABASE_PRIVILEGES, MIGRATION_FAILED, TENANT_CONTEXT_REJECTED, TIMEOUT, UNEXPECTED }
    private SafeDiagnostics() {}
    public static Failure classify(Throwable error) {
        Throwable current = error;
        boolean migration = false;
        for (int i = 0; current != null && i < 16; i++, current = current.getCause()) {
            if (current instanceof com.fiscuvia.platform.StartupGuardFailure guard) return switch(guard.kind()) {
                case CONFIGURATION_REJECTED -> Failure.CONFIGURATION_REJECTED;
                case DATABASE_PRIVILEGES -> Failure.DATABASE_PRIVILEGES;
            };
            if (current instanceof com.fiscuvia.platform.internal.tenancy.TenantContextRejected) return Failure.TENANT_CONTEXT_REJECTED;
            migration |= current instanceof org.flywaydb.core.api.FlywayException;
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith("28")) return Failure.DATABASE_AUTHENTICATION;
                if (migration && state != null && !state.startsWith("08") && !state.startsWith("57")) return Failure.MIGRATION_FAILED;
                return Failure.DATABASE_UNAVAILABLE;
            }
            if (current instanceof java.util.concurrent.TimeoutException
                    || current instanceof org.springframework.web.context.request.async.AsyncRequestTimeoutException) return Failure.TIMEOUT;
            if (current instanceof org.springframework.boot.context.properties.bind.BindException) return Failure.CONFIGURATION_REJECTED;
            if (current instanceof org.springframework.core.convert.ConversionException
                    || current instanceof NumberFormatException) return Failure.CONFIGURATION_REJECTED;
            for (StackTraceElement frame : current.getStackTrace()) {
                if (frame.getClassName().equals("com.fiscuvia.platform.internal.ScaffoldRuntimeConfiguration")) return Failure.CONFIGURATION_REJECTED;
                if (frame.getClassName().equals("com.fiscuvia.platform.internal.observability.ObservabilityGuard")
                        || frame.getClassName().equals("com.fiscuvia.platform.internal.observability.ObservabilityConfiguration")) return Failure.CONFIGURATION_REJECTED;
                if (frame.getClassName().equals("com.fiscuvia.platform.internal.DatabaseRuntimeConfiguration")) return Failure.DATABASE_PRIVILEGES;
            }
        }
        if (migration) return Failure.MIGRATION_FAILED;
        return Failure.UNEXPECTED;
    }
    public static String fingerprint(Throwable error) {
        // Only an approved code location + line, never exception text, SQL, filenames or caller data.
        String location = "UNREGISTERED";
        if (error != null) for (StackTraceElement frame : error.getStackTrace()) {
            String mapped = switch (frame.getClassName()) {
                case "com.fiscuvia.platform.internal.http.PlatformInfoController" -> "PLATFORM_INFO";
                case "com.fiscuvia.platform.internal.ScaffoldRuntimeConfiguration" -> "CONFIGURATION";
                case "com.fiscuvia.platform.internal.DatabaseRuntimeConfiguration" -> "DATABASE_GUARD";
                case "com.fiscuvia.platform.internal.tenancy.JdbcTenantTransactions" -> "TENANT_TRANSACTION";
                default -> null;
            };
            if (mapped != null) { location = mapped + ":" + Math.max(0, frame.getLineNumber()); break; }
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((classify(error).name() + ":" + location).getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
