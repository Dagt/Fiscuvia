package com.fiscuvia.taxpayers.internal;

import com.fiscuvia.platform.*;
import java.io.*;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Bounded before deserialization, after the existing authentication/CSRF filters. Never logs or caches a body. */
@ControllerAdvice(assignableTypes=TaxpayerController.class)
class TaxpayerBodyLimit extends RequestBodyAdviceAdapter {
    @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return true;}
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c) throws IOException {
        byte[] bytes=input.getBody().readNBytes(65537);
        if(bytes.length>65536)throw new ApiFailure(ApiErrorCode.PAYLOAD_TOO_LARGE);
        return new HttpInputMessage(){public HttpHeaders getHeaders(){return input.getHeaders();}public InputStream getBody(){return new ByteArrayInputStream(bytes);}};
    }
}
