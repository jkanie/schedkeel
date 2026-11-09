package io.github.jkanie.schedkeel.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of a create or update request. Optional fields fall back to the
 * defaults below.
 */
public record JobRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
        String name,

        @Size(max = 500) String description,
        @NotBlank @Size(max = 120) String cron,
        @Size(max = 64) String timeZone,
        @NotNull JobType type,
        @NotBlank @Size(max = 2000) String target,
        @Size(max = 100_000) String payload,
        Boolean enabled,
        ConcurrencyPolicy concurrency,
        @Min(0) @Max(10) Integer maxRetries,
        @Min(1) @Max(86_400) Integer retryBackoffSeconds,
        @Min(1) @Max(86_400) Integer timeoutSeconds) {

    static final String NAME_PATTERN = "[a-z0-9][a-z0-9-]*";
    static final String NAME_MESSAGE = "use lower-case letters, digits and hyphens";

    String timeZoneOrDefault() {
        return timeZone == null || timeZone.isBlank() ? "UTC" : timeZone.trim();
    }

    boolean enabledOrDefault() {
        return enabled == null || enabled;
    }

    ConcurrencyPolicy concurrencyOrDefault() {
        return concurrency == null ? ConcurrencyPolicy.FORBID : concurrency;
    }

    int maxRetriesOrDefault() {
        return maxRetries == null ? 0 : maxRetries;
    }

    int retryBackoffSecondsOrDefault() {
        return retryBackoffSeconds == null ? 30 : retryBackoffSeconds;
    }

    int timeoutSecondsOrDefault() {
        return timeoutSeconds == null ? 300 : timeoutSeconds;
    }
}
