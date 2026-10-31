package com.fiscuvia.platform.internal.http;

import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.HttpProblemResponder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class FilterProblemResponder implements HttpProblemResponder {
    private final JsonMapper json;
    FilterProblemResponder(JsonMapper json) { this.json = json; }
    @Override public void write(ApiErrorCode code, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(code.status());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        // ProblemDetail uses Spring's Jackson mixin, shared with MVC.
        response.getOutputStream().write(json.writeValueAsBytes(SafeProblems.create(code, request)));
    }
}
