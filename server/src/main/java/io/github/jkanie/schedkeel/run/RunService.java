package io.github.jkanie.schedkeel.run;

import io.github.jkanie.schedkeel.job.ConcurrencyPolicy;
import io.github.jkanie.schedkeel.job.JobDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, claims and finishes runs. */
@Service
public class RunService {

    private final RunRepository runs;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RunService(RunRepository runs, JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
        this.runs = runs;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Queues a first attempt of a job. A job with the {@code FORBID} policy
     * gets a {@code SKIPPED} run instead when an earlier run is still active.
     */
    @Transactional
    public Run enqueue(JobDefinition job, TriggerSource source, Instant scheduledFor) {
        Instant now = Instant.now(clock);
        Run run = Run.create(job.id(), job.name(), RunStatus.QUEUED, source, 1, scheduledFor, now);
        if (job.concurrency() == ConcurrencyPolicy.FORBID && hasActiveRun(job.id())) {
            run.skip(now, "Skipped: an earlier run of this job is still queued or running.");
            Run saved = runs.save(run);
            events.publishEvent(finished(saved));
            return saved;
        }
        return runs.save(run);
    }

    /** Queues another attempt of a failed run, to start at {@code notBefore}. */
    @Transactional
    public Run enqueueRetry(Run failed, Instant notBefore) {
        Run retry = Run.create(
                failed.getJobId(),
                failed.getJobName(),
                RunStatus.QUEUED,
                TriggerSource.RETRY,
                failed.getAttempt() + 1,
                failed.getScheduledFor(),
                notBefore);
        return runs.save(retry);
    }

    /**
     * Marks up to {@code limit} due runs as {@code RUNNING} for {@code owner}
     * and returns them. Rows locked by another instance are skipped.
     */
    @Transactional
    public List<Run> claimDue(String owner, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        var now = Instant.now(clock).atOffset(ZoneOffset.UTC);
        List<Long> ids = jdbc.sql("""
                UPDATE runs SET status = 'RUNNING', started_at = :now, owner = :owner
                WHERE id IN (
                    SELECT id FROM runs
                    WHERE status = 'QUEUED' AND not_before <= :now
                    ORDER BY not_before, id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED)
                AND status = 'QUEUED'
                RETURNING id
                """)
                .param("now", now)
                .param("owner", owner)
                .param("limit", limit)
                .query(Long.class)
                .list();
        return runs.findAllById(ids).stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
    }

    /**
     * Moves a running run to a terminal status. Returns the finished run, or
     * empty when the run was not running (finished elsewhere already).
     */
    @Transactional
    public Optional<Run> finish(long runId, RunStatus status, String output) {
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("Not a terminal status: " + status);
        }
        int updated = jdbc.sql("""
                UPDATE runs SET status = :status, finished_at = :now, output = :output
                WHERE id = :id AND status = 'RUNNING'
                """)
                .param("status", status.name())
                .param("now", Instant.now(clock).atOffset(ZoneOffset.UTC))
                .param("output", output)
                .param("id", runId)
                .update();
        if (updated == 0) {
            return Optional.empty();
        }
        Run run = runs.findById(runId).orElseThrow();
        events.publishEvent(finished(run));
        return Optional.of(run);
    }

    /** Runs still marked RUNNING that started before {@code startedBefore}. */
    @Transactional(readOnly = true)
    public List<Run> runningSince(Instant startedBefore) {
        List<Long> ids = jdbc.sql("SELECT id FROM runs WHERE status = 'RUNNING' AND started_at < :before ORDER BY id")
                .param("before", startedBefore.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .list();
        return runs.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public Optional<Run> find(long id) {
        return runs.findById(id);
    }

    @Transactional(readOnly = true)
    public long countByStatus(RunStatus status) {
        return runs.countByStatus(status);
    }

    @Transactional(readOnly = true)
    Page<Run> page(String jobName, RunStatus status, Pageable pageable) {
        if (jobName != null && status != null) {
            return runs.findByJobNameAndStatus(jobName, status, pageable);
        }
        if (jobName != null) {
            return runs.findByJobName(jobName, pageable);
        }
        if (status != null) {
            return runs.findByStatus(status, pageable);
        }
        return runs.findAll(pageable);
    }

    Run get(long id) {
        return find(id).orElseThrow(() -> new RunNotFoundException(id));
    }

    private boolean hasActiveRun(long jobId) {
        return jdbc.sql("SELECT count(*) FROM runs WHERE job_id = :id AND status IN ('QUEUED', 'RUNNING')")
                        .param("id", jobId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    private static RunFinished finished(Run run) {
        return new RunFinished(
                run.getId(),
                run.getJobId(),
                run.getJobName(),
                run.getStatus(),
                run.getTriggerSource(),
                run.getAttempt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
