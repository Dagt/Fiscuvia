package com.fiscuvia.platform.internal.http;

import com.fiscuvia.platform.internal.observability.*;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class CorrelationFilterTests {
    @Test void restoresContextOnSuccessAndEscapingException() throws Exception {
        var filter = new CorrelationFilter(new HttpTelemetry(Tracer.NOOP));
        for (boolean fail : new boolean[]{false, true}) {
            var request = new MockHttpServletRequest("POST", "/private/canary");
            request.addHeader("X-Correlation-Id", "caller-controlled-canary");
            request.setContent("private-body-canary".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            try {
                filter.doFilter(request, response, (req, res) -> {
                    assertThat(MDC.get("correlationId")).isEqualTo(response.getHeader("X-Correlation-Id"));
                    assertThat(SafeLogContext.current().traceId()).isNull();
                    if (fail) throw new ServletException("private-exception-canary");
                });
                assertThat(fail).isFalse();
            } catch (ServletException expected) { assertThat(fail).isTrue(); }
            assertThat(response.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
            assertThat(MDC.get("correlationId")).isNull();
            assertThat(SafeLogContext.current()).isNull();
        }
    }
}
