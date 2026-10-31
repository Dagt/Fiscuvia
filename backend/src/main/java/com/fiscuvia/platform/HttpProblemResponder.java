package com.fiscuvia.platform;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Safe transport boundary for filters outside the platform module. */
public interface HttpProblemResponder {
    void write(ApiErrorCode code, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
