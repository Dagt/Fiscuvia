package com.fiscuvia.platform.internal.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public final class LifecycleDiagnostics implements ApplicationListener<ApplicationEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(LifecycleDiagnostics.class);
    @Override public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationReadyEvent ready) {
            Integer port = ready.getApplicationContext() instanceof WebServerApplicationContext web ? web.getWebServer().getPort() : null;
            LOG.atInfo().addKeyValue("operationalEvent", new OperationalEvent(OperationalEvent.Code.APPLICATION_READY,
                    null, null, null, null, port, null, null)).log("APPLICATION_READY");
        } else if (event instanceof ApplicationFailedEvent failed) {
            LOG.atError().addKeyValue("operationalEvent", new OperationalEvent(OperationalEvent.Code.APPLICATION_FAILED,
                    null, null, null, null, null, SafeDiagnostics.classify(failed.getException()),
                    SafeDiagnostics.fingerprint(failed.getException()))).log("APPLICATION_FAILED");
        }
    }
}
