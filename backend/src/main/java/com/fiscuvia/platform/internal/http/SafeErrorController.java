package com.fiscuvia.platform.internal.http;

import com.fiscuvia.platform.ApiErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Servlet error dispatch fallback: never serialize ErrorAttributes or caller query parameters. */
@Controller
class SafeErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        ApiErrorCode code = ApiErrorCode.forStatus(attribute instanceof Integer status ? status : 500);
        return ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Correlation-Id", CorrelationFilter.id(request)).header("Cache-Control", "no-store")
                .body(SafeProblems.create(code, request));
    }
}
