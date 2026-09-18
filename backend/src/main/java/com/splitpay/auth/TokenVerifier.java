package com.splitpay.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class TokenVerifier {
    private final Environment environment;
    private FirebaseAuth firebase;
    public TokenVerifier(Environment environment) { this.environment = environment; }
    public Identity verify(String token) throws Exception {
        if (environment.matchesProfiles("demo")) {
            String expected = environment.getProperty("SPLITPAY_DEMO_TOKEN", "");
            if (expected.length() >= 32 && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
                return new Identity("demo-pratik", "pratik@splitpay.demo", "Pratik");
        }
        FirebaseToken verified = firebase().verifyIdToken(token, true);
        if (verified.getEmail() == null) throw new IllegalArgumentException("An email address is required");
        return new Identity(verified.getUid(), verified.getEmail(), verified.getName() == null ? verified.getEmail().split("@")[0] : verified.getName());
    }
    private synchronized FirebaseAuth firebase() throws Exception {
        if (firebase == null) {
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(environment.getProperty("FIREBASE_PROJECT_ID")).build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            firebase = FirebaseAuth.getInstance(app);
        }
        return firebase;
    }
}
