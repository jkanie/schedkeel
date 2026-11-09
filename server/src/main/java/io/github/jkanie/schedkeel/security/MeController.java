package io.github.jkanie.schedkeel.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the dashboard who is signed in and what they may do. */
@RestController
public class MeController {

    /** The signed-in user. */
    public record Me(String username, List<String> roles, boolean canChange) {}

    @GetMapping("/api/me")
    public Me me(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new Me(auth.getName(), roles, roles.contains(SecurityConfiguration.ADMIN));
    }
}
