package com.fiscuvia.platform.internal.http;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PlatformInfoController {
    record PlatformInfoResponse(String productName, String tagline, String apiVersion) {}

    @GetMapping(value = "/api/v1/public/info", produces = MediaType.APPLICATION_JSON_VALUE)
    PlatformInfoResponse info() {
        return new PlatformInfoResponse("Fiscuvia", "Todo tu despacho, una sola vía.", "1.0.0");
    }
}
