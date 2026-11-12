package com.fiscuvia.identity.internal;

import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.HttpProblemResponder;
import jakarta.servlet.DispatcherType;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {
    @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordAccounts() {
        return name->{ throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Password authentication unavailable"); };
    }
    @Bean org.springframework.security.oauth2.jwt.JwtDecoderFactory<ClientRegistration> identityTokenDecoders() {
        var factory=new org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(client->new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                new org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator(client),
                new org.springframework.security.oauth2.jwt.JwtTimestampValidator(java.time.Duration.ZERO)));
        return factory;
    }
    @Bean @ConditionalOnProperty(name="fiscuvia.identity.enabled",havingValue="true")
    ClientRegistrationRepository oidcClients(IdentitySettings settings) {
        ClientRegistration client=ClientRegistrations.fromIssuerLocation(settings.issuer()).registrationId("local")
                .clientId(settings.clientId()).clientSecret(settings.clientSecret()).scope(settings.localContactsEnabled()?new String[]{"openid","email","profile"}:new String[]{"openid"})
                .redirectUri(settings.origin()+"/api/v1/auth/callback/local").clientName("Fiscuvia local").build();
        return new InMemoryClientRegistrationRepository(client);
    }

    @Bean SecurityFilterChain identitySecurity(HttpSecurity http,IdentitySettings settings,IdentityStore store,
            HttpProblemResponder problems,org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> clients,
            org.springframework.core.env.Environment environment) throws Exception {
        var csrf=new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http.authorizeHttpRequests(auth->auth
                .requestMatchers(request->environment.getProperty("fiscuvia.observability.local-metrics",Boolean.class,false)
                        && request.getLocalPort()==environment.getProperty("management.server.port",Integer.class,-1)
                        && (request.getRequestURI().equals("/actuator/metrics") || request.getRequestURI().startsWith("/actuator/metrics/"))).permitAll()
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health","/actuator/health/liveness","/actuator/health/readiness",
                        "/error","/api/v1/public/info","/api/v1/auth/csrf","/api/v1/auth/status","/api/v1/auth/login",
                        "/api/v1/auth/authorize/local","/api/v1/auth/callback/local").permitAll()
                .requestMatchers("/api/v1/me","/api/v1/auth/tenant","/api/v1/auth/invitations/**","/api/v1/auth/memberships/**").authenticated()
                .requestMatchers("/api/v1/tenants/*/settings").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/tenants/*/entitlements").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/tenants/*/team/invitation-options").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/tenants/*/team/invitations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/tenants/*/team/members","/api/v1/tenants/*/team/invitations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/tenants/*/team/members/*/status","/api/v1/tenants/*/team/members/*/role","/api/v1/tenants/*/team/invitations/*/revoke").authenticated()
                .anyRequest().denyAll())
            .csrf(config->config.csrfTokenRepository(csrf))
            .requestCache(cache->cache.disable())
            .headers(headers->headers.referrerPolicy(policy->policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((request,response,error)->problems.write(
                        request.getRequestURI().equals("/api/v1/me") || request.getRequestURI().startsWith("/api/v1/auth/")
                                || request.getRequestURI().matches("/api/v1/tenants/[^/]+/(settings|entitlements|team/invitation-options|team/invitations|team/members|team/members/[^/]+/(status|role)|team/invitations/[^/]+/revoke)")
                                ? ApiErrorCode.AUTHENTICATION_REQUIRED : ApiErrorCode.NOT_FOUND,request,response))
                .accessDeniedHandler((request,response,error)->problems.write(ApiErrorCode.ACCESS_DENIED,request,response)))
            .sessionManagement(session->session.sessionFixation(fixation->fixation.changeSessionId()))
            .logout(logout->logout.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("__Host-FiscuviaSession")
                .logoutSuccessHandler((request,response,auth)->{ response.setHeader("Cache-Control","no-store"); response.setStatus(204); }))
            .addFilterBefore(new SessionValidityFilter(store,problems),AnonymousAuthenticationFilter.class);
        if (settings.enabled()) {
            var resolver=new DefaultOAuth2AuthorizationRequestResolver(clients.getObject(),"/api/v1/auth/authorize");
            resolver.setAuthorizationRequestCustomizer(builder->{
                OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
                // Reauthentication is real at the IdP; no application checkbox can assert MFA.
                builder.additionalParameters(values->{ values.put("prompt","login"); values.put("max_age","0"); values.put("acr_values","2"); });
            });
            http.oauth2Login(login->login.loginPage("/login").clientRegistrationRepository(clients.getObject())
                .authorizedClientRepository(new NoStoredAuthorizedClients())
                .authorizationEndpoint(endpoint->endpoint.baseUri("/api/v1/auth/authorize").authorizationRequestResolver(new LoginRequestResolver(resolver)))
                .redirectionEndpoint(endpoint->endpoint.baseUri("/api/v1/auth/callback/*"))
                .successHandler((request,response,auth)->{
                    response.setHeader("Cache-Control","no-store"); response.sendRedirect(settings.origin()+"/app");
                })
                .failureHandler((request,response,error)->{
                    // Consume protocol state even on failure; never retain exception/claims in a session.
                    if (request.getSession(false)!=null) request.getSession(false).invalidate();
                    response.setHeader("Cache-Control","no-store"); response.sendRedirect(settings.origin()+"/login?error=autenticacion");
                })
                .withObjectPostProcessor(new ObjectPostProcessor<org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter>() {
                    @Override public <O extends org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter> O postProcess(O filter) {
                        filter.setAuthenticationFailureHandler((request,response,error)->problems.write(ApiErrorCode.AUTHENTICATION_REQUIRED,request,response));
                        return filter;
                    }
                })
                .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                    @Override public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                        filter.setAuthenticationDetailsSource(request->null);
                        filter.setAuthenticationResultConverter(token->{
                            if (!(token.getPrincipal() instanceof OidcUser oidc)) throw new OAuth2AuthenticationException("invalid_identity");
                            long now=Instant.now().getEpochSecond();
                            Instant authenticated=oidc.getIdToken().getAuthenticatedAt();
                            if (authenticated==null || authenticated.getEpochSecond()<now-120 || authenticated.getEpochSecond()>now+30)
                                throw new OAuth2AuthenticationException("reauthentication_required");
                            long mfa=0;
                            // This profile is enabled ONLY with the reviewed local realm's native LoA2 password+OTP flow.
                            // Unknown production provider/claim semantics are deliberately denied.
                            var methods=oidc.getIdToken().getAuthenticationMethods();
                            if ("keycloak-local-otp-v1".equals(settings.mfaPolicy()) && "2".equals(oidc.getIdToken().getAuthenticationContextClass())
                                    && methods!=null && methods.containsAll(java.util.List.of("pwd","otp")))
                                mfa=authenticated.getEpochSecond();
                            IdentityStore.Actor actor;
                            try { actor=store.resolve(oidc.getIssuer().toString(),oidc.getSubject(),mfa,
                                settings.localContactsEnabled()?LocalContact.verified(oidc.getIdToken().getClaimAsString("email"),
                                    oidc.getIdToken().getClaimAsString("name"),Boolean.TRUE.equals(oidc.getIdToken().getClaimAsBoolean("email_verified"))):null); }
                            catch(com.fiscuvia.platform.ApiFailure invalid) { throw new OAuth2AuthenticationException("invalid_identity"); }
                            var principal=new AppPrincipal(actor.id(),oidc.getIssuer().toString(),oidc.getSubject(),actor.version(),
                                    authenticated.getEpochSecond(),now+28800,mfa);
                            return new OAuth2AuthenticationToken(principal,principal.getAuthorities(),token.getClientRegistration().getRegistrationId());
                        });
                        return filter;
                    }
                }));
        }
        return http.build();
    }
}
