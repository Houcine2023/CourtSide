package com.courtside.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

@Configuration
// Activates @PreAuthorize / @PostAuthorize. Without it those annotations are
// silently ignored — every endpoint would be open to any authenticated user.
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter) throws Exception {

        http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // Spring re-dispatches failed requests internally to /error.
            // Without this rule, that internal hop is blocked and real
            // 404/500 responses get masked as 403.
            .dispatcherTypeMatchers(DispatcherType.ERROR)
            .permitAll()
            .requestMatchers(
                "/api/v1/auth/**",
                "/actuator/health"
            )
            .permitAll()
            // Browsing clubs and courts is public — visitors must be able to see
            // what a club offers before creating an account. Only GET: the write
            // verbs on the same paths still fall through to authenticated().
            .requestMatchers(HttpMethod.GET, "/api/v1/clubs/**", "/api/v1/courts/**")
            .permitAll()
            .anyRequest()
            .authenticated()
        )
        // How to answer an unauthenticated request. Without an entry point,
        // Spring Security falls back to a blanket 403; we want an honest 401.
        .exceptionHandling(ex -> ex.authenticationEntryPoint(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
        ))
        .addFilterBefore(
            jwtAuthFilter,
            UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
        
    }
}
