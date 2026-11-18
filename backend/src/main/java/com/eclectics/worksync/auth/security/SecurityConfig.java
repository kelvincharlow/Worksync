package com.eclectics.worksync.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.eclectics.worksync.auth.config.PasswordEncodingConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
@Configuration
@EnableWebSecurity
@Import(PasswordEncodingConfiguration.class)
public class SecurityConfig {
     @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

 private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Spring Security places CorsFilter before authentication, including JwtFilter.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 1. Public endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                /// 2. Admin routes require BOTH ROLE_ADMIN and STATUS_ACTIVE
                .requestMatchers("/api/v1/admin/**").access(
                 AuthorizationManagers.allOf(
                 AuthorityAuthorizationManager.hasRole("ADMIN"),
                 AuthorityAuthorizationManager.hasAuthority("STATUS_ACTIVE")
    )
)
// 3. Only viewing own profile/approval status is allowed for PENDING_APPROVAL
                .requestMatchers("/api/v1/users/me").authenticated()

                // 4. Notifications and all other endpoints strictly require an ACTIVE account
                .requestMatchers("/api/v1/notifications/**").hasAuthority("STATUS_ACTIVE")
                .anyRequest().hasAuthority("STATUS_ACTIVE")
            )
            // 5. REST 401 and 403 handling
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
            )
            // 6. Execute JwtFilter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Prevents Spring Boot from auto-registering JwtFilter as a generic servlet filter,
    // ensuring it runs ONLY inside the Spring Security filter chain.
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Parse configured origins (splits comma-separated strings if multiple)
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        origins.forEach(SecurityConfig::validateOrigin);
        configuration.setAllowedOrigins(origins);

        // 2. Explicit HTTP methods allowed
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));

        // 3. Explicit headers allowed from the browser
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Tokens are sent explicitly in Authorization / JSON, not ambient browser cookies.
        configuration.setAllowCredentials(false);

        // 6. Cache the preflight response for 1 hour to reduce OPTIONS requests
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void validateOrigin(String value) {
        URI origin;
        try {
            origin = URI.create(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("CORS allowlist must contain explicit HTTP(S) origins");
        }
        if (value.contains("*") || origin.getHost() == null
                || !("http".equalsIgnoreCase(origin.getScheme()) || "https".equalsIgnoreCase(origin.getScheme()))
                || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("CORS allowlist must contain explicit HTTP(S) origins without paths or wildcards");
        }
    }
}