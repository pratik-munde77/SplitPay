package com.splitpay.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(FirebaseFilter.class);
    private static final Set<String> SESSION_ERRORS = Set.of("EXPIRED_ID_TOKEN", "REVOKED_ID_TOKEN", "INVALID_ID_TOKEN", "USER_DISABLED", "USER_NOT_FOUND", "TENANT_ID_MISMATCH");
    private final TokenVerifier verifier;
    public FirebaseFilter(TokenVerifier verifier) { this.verifier = verifier; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Identity identity = verifier.verify(header.substring(7));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
            } catch (Exception error) {
                String code = error instanceof FirebaseAuthException authError
                        ? String.valueOf(authError.getAuthErrorCode()) : error.getClass().getSimpleName();
                boolean sessionError = error instanceof IllegalArgumentException
                        || (error instanceof FirebaseAuthException && SESSION_ERRORS.contains(code));
                Throwable cause = error.getCause() == null ? error : error.getCause();
                // Never log exception messages, tokens or credential contents.
                log.warn("Firebase authentication failed: code={}, cause={}, credentialFileReadable={}",
                        code, cause.getClass().getSimpleName(), credentialFileReadable());
                SecurityContextHolder.clearContext();
                response.setStatus(sessionError ? 401 : 503);
                response.setContentType("application/json");
                response.getWriter().write(sessionError
                        ? "{\"message\":\"Invalid or expired session. Sign in again.\"}"
                        : "{\"message\":\"Server authentication is unavailable. Please try again later.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    private static boolean credentialFileReadable() {
        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        try { return path != null && java.nio.file.Files.isReadable(java.nio.file.Path.of(path)); }
        catch (RuntimeException ignored) { return false; }
    }
}
