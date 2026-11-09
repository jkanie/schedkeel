package io.github.jkanie.schedkeel.run;

import java.time.Instant;
import org.springframework.hateoas.server.core.Relation;

/** The API view of a run. */
@Relation(collectionRelation = "runs", itemRelation = "run")
public record RunResource(
        long id,
        String jobName,
        RunStatus status,
        TriggerSource trigger,
        int attempt,
        Instant scheduledFor,
        Instant notBefore,
        Instant startedAt,
        Instant finishedAt,
        String owner,
        String output) {

    static RunResource of(Run run) {
        return new RunResource(
                run.getId(),
                run.getJobName(),
                run.getStatus(),
                run.getTriggerSource(),
                run.getAttempt(),
                run.getScheduledFor(),
                run.getNotBefore(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getOwner(),
                run.getOutput());
    }
}
