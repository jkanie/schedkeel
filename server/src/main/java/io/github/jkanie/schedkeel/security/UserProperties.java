package io.github.jkanie.schedkeel.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Users under {@code schedkeel.security.users}. Passwords come from the
 * environment (see application.yml), never from the repository.
 */
@ConfigurationProperties("schedkeel.security")
record UserProperties(List<User> users) {

    record User(String username, String password, List<String> roles) {}

    UserProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }
}
