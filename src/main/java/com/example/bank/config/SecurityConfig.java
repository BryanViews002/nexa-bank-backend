package com.example.bank.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration config = new CorsConfiguration();
                        config.addAllowedOrigin("http://localhost:3000"); // React frontend
                        config.addAllowedMethod("*");
                        config.addAllowedHeader("*");
                        config.setAllowCredentials(true);
                        return config;
                    }
                }))
                .csrf(csrf -> csrf.disable()) // CSRF disabled for API testing
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers("/register", "/login").permitAll()
                        .requestMatchers("/auth/verify-otp", "/auth/request-password-reset",
                                "/auth/confirm-password-reset", "/auth/reset-password").permitAll()
                        // FIXED: Allow get-otp without authentication (users need this to login)
                        .requestMatchers("/auth/get-otp", "/api/auth/get-otp").permitAll()
                        .requestMatchers("/api/register", "/api/login", "/api/auth/verify-otp",
                                "/api/auth/request-password-reset", "/api/auth/confirm-password-reset",
                                "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/test", "/api/health").permitAll()
                        // Error handling
                        .requestMatchers("/error").permitAll()
                        // Static resources
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/webjars/**", "/static/**").permitAll()
                        // Health check endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Admin endpoints
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // Authenticated endpoints
                        .requestMatchers("/accounts/**", "/api/accounts/**",
                                "/transactions/**", "/api/transactions/**").authenticated()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(5)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json");
                            try {
                                response.getWriter().write("{\"message\":\"Logged out successfully from Nexa\"}");
                            } catch (Exception e) {
                                log.error("Logout response error", e);
                            }
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("Access denied for request: {} {} - {}",
                                    request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            try {
                                response.getWriter().write("{\"error\":\"Access denied\",\"message\":\"" +
                                        accessDeniedException.getMessage() + "\"}");
                            } catch (Exception e) {
                                log.error("Error writing access denied response", e);
                            }
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.info("Authentication required for: {} {}", request.getMethod(), request.getRequestURI());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            try {
                                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required for Nexa\"}");
                            } catch (Exception e) {
                                log.error("Error writing auth entry point response", e);
                            }
                        })
                )
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher() {
        return new DefaultAuthenticationEventPublisher();
    }

    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }
}