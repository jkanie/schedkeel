package io.github.jkanie.schedkeel.housekeeping;

import java.time.Instant;

/**
 * One row of an archive CSV file. Public because Spring Batch reads the
 * record components by reflection.
 */
public record ArchivedRun(
        long id,
        String jobName,
        String status,
        String trigger,
        int attempt,
        Instant scheduledFor,
        Instant startedAt,
        Instant finishedAt) {}
