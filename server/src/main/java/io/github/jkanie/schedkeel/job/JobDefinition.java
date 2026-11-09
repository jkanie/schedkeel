package io.github.jkanie.schedkeel.job;

/** An immutable snapshot of a job, shared with the other modules. */
public record JobDefinition(
        long id,
        String name,
        String cron,
        String timeZone,
        JobType type,
        String target,
        String payload,
        boolean enabled,
        ConcurrencyPolicy concurrency,
        int maxRetries,
        int retryBackoffSeconds,
        int timeoutSeconds) {}
