package io.github.jkanie.schedkeel.job;

import java.time.Instant;
import org.springframework.hateoas.server.core.Relation;

/** The API view of a job. */
@Relation(collectionRelation = "jobs", itemRelation = "job")
public record JobResource(
        String name,
        String description,
        String cron,
        String timeZone,
        JobType type,
        String target,
        String payload,
        boolean enabled,
        ConcurrencyPolicy concurrency,
        int maxRetries,
        int retryBackoffSeconds,
        int timeoutSeconds,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
