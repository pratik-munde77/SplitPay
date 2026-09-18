package com.splitpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, com.splitpay.auth.FirebaseFilter filter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> response.sendError(401)))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/health", "/api/webhooks/razorpay", "/api/webhooks/paypal", "/api/paypal/return").permitAll().anyRequest().authenticated())
            .build();
    }
    @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordLogin() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Firebase authentication required"); };
    }
}
