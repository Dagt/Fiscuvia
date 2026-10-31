package com.fiscuvia.platform.internal.observability;

import java.util.List;
import java.util.UUID;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class StructuredLoggingTests {
    @Test void accessDecisionsExposeOnlyTypedCategoriesAndTrustedCorrelation() throws Exception {
        var e=new LoggingEvent(); e.setTimeStamp(0); e.setLevel(Level.INFO); e.setLoggerName("com.fiscuvia.identity.authorization");
        e.setMessage(PRIVATE); e.addKeyValuePair(new KeyValuePair("resourceId",PRIVATE));
        e.addKeyValuePair(new KeyValuePair("accessDecision",new com.fiscuvia.platform.AccessDecisionEvent(
            com.fiscuvia.platform.AccessDecisionEvent.Decision.DENY,com.fiscuvia.platform.AccessDecisionEvent.Reason.RESOURCE_UNASSIGNED,
            com.fiscuvia.platform.AccessDecisionEvent.Category.TAXPAYER,com.fiscuvia.platform.AccessDecisionEvent.Operation.EXPORT)));
        var id=UUID.randomUUID();
        try(var context=new SafeLogContext(new SafeLogContext.Context(id,null,null))) {
            String json=formatter.format(e); var fields=new JsonMapper().readTree(json);
            assertThat(fields.path("event").asString()).isEqualTo("ACCESS_DECISION");
            assertThat(fields.path("module").asString()).isEqualTo("identity");
            assertThat(fields.path("reason_code").asString()).isEqualTo("RESOURCE_UNASSIGNED");
            assertThat(fields.path("correlation_id").asString()).isEqualTo(id.toString());
            assertThat(fields.propertyNames()).containsExactlyInAnyOrder("timestamp","application","severity","component","module","event",
                "correlation_id","decision","reason_code","resource_category","operation_category");
            assertThat(json).doesNotContain(PRIVATE,"resourceId","tenantId","userId","SQL");
        }
    }
    private static final String PRIVATE = "DEMO900101XX1-private-token-cookie-CFDI-query";
    private final AllowlistLogFormatter formatter = new AllowlistLogFormatter();

    @Test void projectionRejectsMessagesArgumentsMdcThrowableAndArbitraryAttributes() throws Exception {
        var event = new LoggingEvent();
        event.setTimeStamp(0);
        event.setLevel(Level.ERROR);
        event.setLoggerName("com.fiscuvia." + PRIVATE);
        event.setMessage(PRIVATE + " {}\n");
        event.setArgumentArray(new Object[] {PRIVATE});
        event.setMDCPropertyMap(java.util.Map.of("traceId", PRIVATE, "Authorization", PRIVATE));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException(PRIVATE)));
        event.addKeyValuePair(new KeyValuePair("password", PRIVATE));
        event.addKeyValuePair(new KeyValuePair("operationalEvent", PRIVATE));
        String json = formatter.format(event);
        var fields = new JsonMapper().readTree(json);
        assertThat(fields.path("timestamp").asString()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(fields.path("severity").asString()).isEqualTo("ERROR");
        assertThat(fields.path("event").asString()).isEqualTo("RUNTIME_LOG");
        assertThat(fields.propertyNames()).containsExactlyInAnyOrder("timestamp", "application", "severity", "component", "module", "event");
        assertThat(json).doesNotContain(PRIVATE, "Authorization", "password", "stack", "exception");
        assertThat(json.strip()).doesNotContain("\n", "\r");
    }

    @Test void typedEventsAndTrustedContextAreSerializedWithNoInventedTrace() throws Exception {
        var event = new LoggingEvent(); event.setTimeStamp(0); event.setLevel(Level.INFO); event.setLoggerName("com.fiscuvia.platform");
        event.addKeyValuePair(new KeyValuePair("operationalEvent", new OperationalEvent(OperationalEvent.Code.HTTP_REQUEST_COMPLETED,
                PRIVATE, PRIVATE, 404, 2.5, null, null, null)));
        var id = UUID.randomUUID();
        try (var context = new SafeLogContext(new SafeLogContext.Context(id, null, null))) {
            var fields = new JsonMapper().readTree(formatter.format(event));
            assertThat(fields.path("correlation_id").asString()).isEqualTo(id.toString());
            assertThat(fields.path("method").asString()).isEqualTo("OTHER");
            assertThat(fields.path("route").asString()).isEqualTo("UNMAPPED");
            assertThat(fields.path("duration_ms").asDouble()).isEqualTo(2.5);
            assertThat(fields.has("trace_id")).isFalse();
        }
        assertThat(SafeLogContext.current()).isNull();
    }

    @Test void scopeRestoresMdcAndTrustedContextEvenWhenExceptionEscapes() {
        var before = MDC.getCopyOfContextMap();
        try {
            MDC.put("outer", "safe");
            var initial = MDC.getCopyOfContextMap();
            assertThatThrownBy(() -> {
                try (var context = new SafeLogContext(new SafeLogContext.Context(UUID.randomUUID(), "1".repeat(32), "2".repeat(16)))) {
                    assertThat(MDC.get("correlationId")).isNotBlank();
                    throw new IllegalStateException(PRIVATE);
                }
            }).isInstanceOf(IllegalStateException.class);
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(initial);
            assertThat(SafeLogContext.current()).isNull();
        } finally { MDC.clear(); if (before != null) MDC.setContextMap(before); }
    }

    @Test void diagnosticFingerprintNeverUsesPrivateThrowableTextOrFilenames() {
        for(var kind:com.fiscuvia.platform.StartupGuardFailure.Kind.values())
            assertThat(SafeDiagnostics.classify(new com.fiscuvia.platform.StartupGuardFailure(kind)).name()).isEqualTo(kind.name());
        var one = new IllegalStateException(PRIVATE);
        one.setStackTrace(new StackTraceElement[] {new StackTraceElement("com.fiscuvia." + PRIVATE, PRIVATE, PRIVATE, 5)});
        var two = new IllegalStateException("other-private-input"); two.setStackTrace(new StackTraceElement[0]);
        assertThat(SafeDiagnostics.fingerprint(one)).isEqualTo(SafeDiagnostics.fingerprint(two)).matches("[a-f0-9]{16}");
        assertThat(SafeDiagnostics.classify(new java.sql.SQLException(PRIVATE, "28P01")))
                .isEqualTo(SafeDiagnostics.Failure.DATABASE_AUTHENTICATION);
        assertThat(SafeDiagnostics.classify(new org.flywaydb.core.api.FlywayException(PRIVATE,
                new java.sql.SQLException(PRIVATE, "42601")))).isEqualTo(SafeDiagnostics.Failure.MIGRATION_FAILED);
        assertThat(SafeDiagnostics.classify(new org.flywaydb.core.api.FlywayException(PRIVATE,
                new java.sql.SQLException(PRIVATE, "08001")))).isEqualTo(SafeDiagnostics.Failure.DATABASE_UNAVAILABLE);
    }
}
