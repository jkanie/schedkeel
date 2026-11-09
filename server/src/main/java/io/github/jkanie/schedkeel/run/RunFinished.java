package io.github.jkanie.schedkeel.run;

import java.time.Instant;

/** Published when a run reaches a terminal status. */
public record RunFinished(
        long runId,
        long jobId,
        String jobName,
        RunStatus status,
        TriggerSource trigger,
        int attempt,
        Instant startedAt,
        Instant finishedAt) {}
