package com.splitpay.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseFilter extends OncePerRequestFilter {
    private final TokenVerifier verifier;
    public FirebaseFilter(TokenVerifier verifier) { this.verifier = verifier; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Identity identity = verifier.verify(header.substring(7));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
            } catch (Exception ignored) {
                response.setStatus(401); response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Invalid or expired session. Sign in again.\"}"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
