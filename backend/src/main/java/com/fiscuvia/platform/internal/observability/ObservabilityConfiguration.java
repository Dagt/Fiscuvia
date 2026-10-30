package com.fiscuvia.platform.internal.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.observation.transport.ReceiverContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {
    @Bean(destroyMethod = "close")
    SdkTracerProvider localTraceProvider(@Value("${management.tracing.sampling.probability:0.1}") double probability,
            ObjectProvider<LocalTracingCustomizer> customizers) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) throw new IllegalArgumentException("Invalid sampling configuration");
        var builder = SdkTracerProvider.builder().setSampler(Sampler.traceIdRatioBased(probability))
                .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "fiscuvia-backend")))
                .setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(8).setMaxNumberOfEvents(1)
                        .setMaxNumberOfLinks(0).setMaxAttributeValueLength(80).build());
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build(); // No processor/exporter, environment autoconfiguration or global SDK.
    }

    @Bean
    OpenTelemetrySdk localOpenTelemetry(SdkTracerProvider provider) {
        return OpenTelemetrySdk.builder().setTracerProvider(provider).setPropagators(ContextPropagators.noop()).build();
    }

    @Bean
    Tracer localMicrometerTracer(OpenTelemetrySdk sdk) {
        var listener = new Slf4JEventListener();
        return new OtelTracer(sdk.getTracer("com.fiscuvia.platform"), new OtelCurrentTraceContext(), listener::onEvent);
    }

    @Bean
    Propagator trustedLocalPropagation(OpenTelemetrySdk sdk) {
        // Public clients cannot choose trusted trace/span IDs or baggage. Authorized propagation is future work.
        return new OtelPropagator(ContextPropagators.noop(), sdk.getTracer("com.fiscuvia.platform"));
    }

    @Bean
    @Order(1000)
    PropagatingReceiverTracingObservationHandler<ReceiverContext<?>> privateReceiverTracing(Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<>(tracer, propagator) {
            @Override public void onStop(ReceiverContext<?> context) {
                if (context.getError() != null || context instanceof ServerRequestObservationContext http
                        && http.getResponse() != null && http.getResponse().getStatus() >= 500) onError(context);
                super.onStop(context);
            }
            @Override public void onError(ReceiverContext<?> context) {
                Throwable original = context.getError();
                try {
                    context.setError(new SafeTraceFailure());
                    super.onError(context);
                } finally { context.setError(original); }
            }
        };
    }

    private static final class SafeTraceFailure extends RuntimeException {
        SafeTraceFailure() { super("OPERATION_FAILED", null, false, false); }
    }

    @Bean
    ObservationPredicate approvedObservations() {
        return (name, context) -> "http.server.requests".equals(name);
    }

    @Bean
    DefaultServerRequestObservationConvention privateHttpConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) { return KeyValues.empty(); }
            @Override public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
                int status = context.getResponse() == null ? 500 : context.getResponse().getStatus();
                if (status < 400 && context.getError() != null) status = 500;
                return KeyValues.of(KeyValue.of("method", SafeRoutes.method(context.getCarrier().getMethod())),
                        KeyValue.of("uri", SafeRoutes.route(context.getPathPattern(), context.getCarrier().getRequestURI())),
                        KeyValue.of("status", Integer.toString(status)), KeyValue.of("outcome", SafeRoutes.outcome(status)),
                        KeyValue.of("exception", context.getError() == null ? "none" : "ERROR"));
            }
            @Override public String getContextualName(ServerRequestObservationContext context) {
                return "http " + SafeRoutes.method(context.getCarrier().getMethod()) + " " + SafeRoutes.route(context.getPathPattern(), context.getCarrier().getRequestURI());
            }
        };
    }

    @Bean
    MeterFilter privateMeterPolicy() { return new PrivateMeterPolicy(); }
}
