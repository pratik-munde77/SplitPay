package com.splitpay.user;

import com.splitpay.auth.Identity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users = users; }
    @Transactional public UserProfile get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Identity identity)) throw new org.springframework.security.access.AccessDeniedException("Sign in required");
        return users.findByFirebaseUid(identity.uid()).orElseGet(() -> {
            UserProfile user = new UserProfile(); user.firebaseUid = identity.uid(); user.email = identity.email().toLowerCase(java.util.Locale.ROOT); user.name = identity.name();
            return users.saveAndFlush(user);
        });
    }
}
