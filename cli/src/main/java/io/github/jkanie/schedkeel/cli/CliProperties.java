package io.github.jkanie.schedkeel.cli;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the server is and who to sign in as. Set them with SCHEDKEEL_URL,
 * SCHEDKEEL_USER and SCHEDKEEL_PASSWORD.
 */
@ConfigurationProperties("schedkeel")
record CliProperties(
        @DefaultValue("http://localhost:8080") URI url,
        @DefaultValue("admin") String user,
        String password) {}
