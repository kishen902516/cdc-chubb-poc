package com.chubb.cdc.debezium.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the CDC management API.
 *
 * <p>Provides authentication and authorization for REST endpoints:</p>
 * <ul>
 *   <li>Dev profile: Basic Authentication with in-memory users</li>
 *   <li>Prod profile: JWT token authentication (placeholder for now)</li>
 *   <li>CSRF disabled (REST API with stateless sessions)</li>
 *   <li>Health endpoints: Public access for monitoring</li>
 *   <li>Management endpoints: Require authentication</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Security filter chain for development profile.
     *
     * <p>Uses HTTP Basic Authentication with in-memory user credentials.
     * Suitable for local development and testing.</p>
     *
     * @param http the HttpSecurity to configure
     * @return the configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session management
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - health checks for monitoring
                .requestMatchers("/api/v1/cdc/health", "/api/v1/cdc/health/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Protected endpoints - require authentication
                .requestMatchers("/api/v1/cdc/config/refresh").hasRole("ADMIN")
                .requestMatchers("/api/v1/cdc/**").authenticated()
                .requestMatchers("/actuator/**").authenticated()

                // Default - require authentication
                .anyRequest().authenticated()
            )

            // Enable HTTP Basic Authentication
            .httpBasic(basic -> {});

        return http.build();
    }

    /**
     * Security filter chain for production profile.
     *
     * <p>Placeholder for JWT token authentication.
     * In production, this should validate JWT tokens from an identity provider.</p>
     *
     * @param http the HttpSecurity to configure
     * @return the configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session management
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules (same as dev for now)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/cdc/health", "/api/v1/cdc/health/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/cdc/config/refresh").hasRole("ADMIN")
                .requestMatchers("/api/v1/cdc/**").authenticated()
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            );

        // TODO: Add JWT token filter for production
        // .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * In-memory user details service for development.
     *
     * <p>Provides two test users:</p>
     * <ul>
     *   <li>cdcuser: Regular user with USER role</li>
     *   <li>cdcadmin: Admin user with ADMIN and USER roles</li>
     * </ul>
     *
     * @return user details service with test users
     */
    @Bean
    @Profile("dev")
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username("cdcuser")
            .password(passwordEncoder().encode("cdcpassword"))
            .roles("USER")
            .build();

        UserDetails admin = User.builder()
            .username("cdcadmin")
            .password(passwordEncoder().encode("cdcadmin"))
            .roles("ADMIN", "USER")
            .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    /**
     * Password encoder for hashing passwords.
     *
     * <p>Uses BCrypt with default strength (10 rounds).</p>
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
