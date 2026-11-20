package com.skillstack.auth;

import com.skillstack.common.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Arrays;

@Configuration
public class SecurityConfiguration {
    private final ApiErrorWriter errors;

    public SecurityConfiguration(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${skillstack.cors-origin}") String origin) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origin.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(1)
    @Profile("!prod")
    SecurityFilterChain localSecurity(HttpSecurity http, DevTokenFilter tokenFilter,
                                      RequestRateLimitFilter rateLimitFilter) throws Exception {
        return common(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/dev-login", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, DevTokenFilter.class)
                .build();
    }

    @Bean
    @Order(1)
    @Profile("prod")
    SecurityFilterChain auth0LoginSecurity(
            HttpSecurity http,
            ClientRegistrationRepository registrations,
            RequestRateLimitFilter rateLimitFilter,
            @Value("${AUTH0_AUDIENCE}") String audience,
            @Value("${skillstack.auth.frontend-uri}") String frontendUri) throws Exception {
        var resolver = auth0AuthorizationRequestResolver(registrations, audience);
        return http
                .securityMatcher("/oauth2/**", "/login/**", "/api/v1/auth/session", "/api/v1/auth/session/logout")
                .cors(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, problem) ->
                                errors.write(response, request, org.springframework.http.HttpStatus.UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED", "Authentication is required"))
                        .accessDeniedHandler((request, response, problem) ->
                                errors.write(response, request, org.springframework.http.HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED", "Access to this resource is denied")))
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                        .defaultSuccessUrl(frontendUri, true)
                        .failureUrl(frontendUri + "?auth=error"))
                .addFilterBefore(rateLimitFilter, OAuth2AuthorizationRequestRedirectFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    @Profile("prod")
    SecurityFilterChain oidcApiSecurity(HttpSecurity http, RequestRateLimitFilter rateLimitFilter) throws Exception {
        return common(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    private OAuth2AuthorizationRequestResolver auth0AuthorizationRequestResolver(
            ClientRegistrationRepository registrations, String audience) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        var pkce = OAuth2AuthorizationRequestCustomizers.withPkce();
        resolver.setAuthorizationRequestCustomizer(request -> {
            pkce.accept(request);
            request.additionalParameters(parameters -> parameters.put("audience", audience));
        });
        return resolver;
    }

    private HttpSecurity common(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, problem) ->
                                errors.write(response, request, org.springframework.http.HttpStatus.UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED", "Authentication is required"))
                        .accessDeniedHandler((request, response, problem) ->
                                errors.write(response, request, org.springframework.http.HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED", "Access to this resource is denied")));
    }
}
