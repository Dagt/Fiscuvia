package com.fiscuvia.platform.internal.http;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fiscuvia.platform.internal.observability.HttpTelemetry;
import com.fiscuvia.platform.internal.observability.SafeLogContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class CorrelationFilter extends OncePerRequestFilter {
    private static final String ATTRIBUTE = CorrelationFilter.class.getName() + ".id";
    private static final String STATE = CorrelationFilter.class.getName() + ".state";
    private final HttpTelemetry telemetry;
    CorrelationFilter(HttpTelemetry telemetry) { this.telemetry = telemetry; }

    private static final class State {
        final long start = System.nanoTime();
        final AtomicBoolean completed = new AtomicBoolean();
        final SafeLogContext.Context context;
        boolean listenerAdded;
        volatile boolean failed;
        State(SafeLogContext.Context context) { this.context = context; }
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException { doFilterInternal(request, response, chain); }

    static String id(HttpServletRequest request) {
        if (!(request.getAttribute(ATTRIBUTE) instanceof String)) {
            request.setAttribute(ATTRIBUTE, UUID.randomUUID().toString());
        }
        return (String) request.getAttribute(ATTRIBUTE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Generate at the trusted boundary; never reflect an arbitrary caller header into responses/logs.
        response.setHeader("X-Correlation-Id", id(request));
        State state = (State) request.getAttribute(STATE);
        if (state == null) {
            state = new State(telemetry.context(UUID.fromString(id(request))));
            request.setAttribute(STATE, state);
        }
        State captured = state;
        try (var scope = new SafeLogContext(state.context)) {
            try { chain.doFilter(request, response); }
            catch (IOException | ServletException | RuntimeException error) { state.failed = true; throw error; }
            finally {
                if (!request.isAsyncStarted()) complete(request, response, state);
                else if (!state.listenerAdded) {
                    state.listenerAdded = true;
                    request.getAsyncContext().addListener(new AsyncListener() {
                        @Override public void onComplete(AsyncEvent event) { complete(request, response, captured); }
                        @Override public void onTimeout(AsyncEvent event) {
                            captured.failed = true;
                            // Registered before Spring's outer observation listener stops the async observation.
                            org.springframework.web.filter.ServerHttpObservationFilter.findObservationContext(request)
                                    .ifPresent(context -> context.setError(new org.springframework.web.context.request.async.AsyncRequestTimeoutException()));
                            try (var timeoutScope = new SafeLogContext(captured.context)) { telemetry.timeout(); }
                        }
                        @Override public void onError(AsyncEvent event) { captured.failed = true; }
                        @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
                    });
                }
            }
        }
    }

    private void complete(HttpServletRequest request, HttpServletResponse response, State state) {
        if (!state.completed.compareAndSet(false, true)) return;
        try (var scope = new SafeLogContext(state.context)) {
            Object template = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            telemetry.complete(request.getMethod(), com.fiscuvia.platform.internal.observability.SafeRoutes.route(
                    template instanceof String path ? path : null, request.getRequestURI()),
                    state.failed ? Math.max(500, response.getStatus()) : response.getStatus(),
                    (System.nanoTime() - state.start) / 1_000_000.0);
        }
    }
}
