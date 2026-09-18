package com.splitpay;

import com.splitpay.auth.FirebaseFilter;
import com.splitpay.auth.TokenVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseFilterTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void missingServerCredentialsAreNotReportedAsExpiredSession() throws Exception {
        var response = failure(new IllegalStateException("initialization failed", new IOException("private detail")));
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("Server authentication is unavailable"));
        assertFalse(response.getContentAsString().contains("private detail"));
    }

    @Test void invalidTokenStillRejectsRequest() throws Exception {
        var response = failure(new IllegalArgumentException("invalid token"));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid or expired session"));
    }

    private MockHttpServletResponse failure(Exception error) throws Exception {
        var verifier = mock(TokenVerifier.class);
        when(verifier.verify("test-token")).thenThrow(error);
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer test-token");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new FirebaseFilter(verifier).doFilter(request, response, chain);
        verifyNoInteractions(chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        return response;
    }
}
