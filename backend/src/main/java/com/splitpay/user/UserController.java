package com.splitpay.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequestMapping("/api/users")
public class UserController {
    private final CurrentUser current;
    private final UserRepository users;
    public UserController(CurrentUser current, UserRepository users) { this.current=current; this.users=users; }
    public record Update(@NotBlank @Size(max=120) String name, @Size(max=30) String phone, @Pattern(regexp="INR") String defaultCurrency, @Size(max=120) String upiId, @Size(max=2000) String photoUrl) {}
    @GetMapping("/me") public UserProfile me() { return current.get(); }
    @PutMapping("/me") @Transactional public UserProfile update(@Valid @RequestBody Update input) {
        UserProfile user = current.get(); user.name=input.name().trim(); user.phone=input.phone()==null?"":input.phone();
        user.upiId=input.upiId()==null?"":input.upiId(); user.photoUrl=input.photoUrl()==null?"":input.photoUrl(); user.updatedAt=Instant.now();
        return users.save(user);
    }
}
