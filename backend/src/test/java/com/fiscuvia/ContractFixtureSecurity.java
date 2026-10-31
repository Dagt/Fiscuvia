package com.fiscuvia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Test controllers for DTO/telemetry regression only. Never imported into OIDC suites or packaged in the JAR. */
@TestConfiguration(proxyBeanMethods=false)
class ContractFixtureSecurity {
    @Bean @Order(0) SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/__contract-test/**","/__observe/**","/__tenancy-test/**")
                .authorizeHttpRequests(auth->auth.anyRequest().permitAll()).csrf(csrf->csrf.disable()).build();
    }
}
