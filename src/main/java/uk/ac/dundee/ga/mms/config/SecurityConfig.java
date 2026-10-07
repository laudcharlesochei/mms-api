package uk.ac.dundee.ga.mms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import uk.ac.dundee.ga.mms.auth.MmsJwtAuthenticationConverter;
import uk.ac.dundee.ga.mms.auth.TokenService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stateless bearer-token API (Section 8.5 SecurityConfig): JWT resource server, CORS limited to
 * the mms-web origin, CSRF off (no cookies), security headers, deny by default.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnWebApplication
public class SecurityConfig {

    private final MmsProperties props;

    public SecurityConfig(MmsProperties props) {
        this.props = props;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MmsJwtAuthenticationConverter converter) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; img-src 'self' data:; "
                                        + "script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/login/mfa",
                                "/api/v1/auth/refresh", "/api/v1/auth/invites/validate", "/api/v1/auth/invites/accept").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/h2-console/**").denyAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(TokenService tokens) {
        if (!props.isEntra()) {
            return tokens.accessDecoder();
        }
        MmsProperties.Entra e = props.getAuth().getEntra();
        String tenant = e.getTenantId();
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys").build();
        List<String> audiences = new ArrayList<>();
        if (e.getAudience() != null && !e.getAudience().isBlank()) {
            audiences.addAll(Arrays.asList(e.getAudience().split(",")));
        }
        if (e.getClientId() != null && !e.getClientId().isBlank()) {
            audiences.add(e.getClientId());
        }
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null
                && jwt.getAudience().stream().anyMatch(a -> audiences.stream().anyMatch(x -> x.trim().equals(a)))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://login.microsoftonline.com/" + tenant + "/v2.0"),
                audienceValidator));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        // trim spaces and trailing slashes: the browser's Origin header never has one
        c.setAllowedOrigins(Arrays.stream(props.getCorsAllowedOrigin().split(","))
                .map(String::trim).map(o -> o.replaceAll("/+$", "")).filter(o -> !o.isEmpty()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "X-Correlation-Id", "X-Client-Request-Id"));
        c.setExposedHeaders(List.of("ETag", "Location", "Content-Disposition", "X-Correlation-Id"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
