package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.InstanceId;
import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;
import io.github.jkanie.schedkeel.run.RunService;
import io.github.jkanie.schedkeel.run.RunStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims due runs and executes each on its own virtual thread. A semaphore
 * caps how many run at once on this instance.
 */
public class Dispatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    /** Extra time past a job's timeout before its run counts as abandoned. */
    static final Duration ABANDON_GRACE = Duration.ofMinutes(1);

    private final RunService runs;
    private final JobCatalog jobs;
    private final Map<JobType, JobRunner> runners = new EnumMap<>(JobType.class);
    private final MeterRegistry meters;
    private final Clock clock;
    private final String owner;
    private final ExecutionProperties props;
    private final Semaphore slots;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    Dispatcher(
            RunService runs,
            JobCatalog jobs,
            List<JobRunner> runners,
            MeterRegistry meters,
            Clock clock,
            InstanceId instance,
            ExecutionProperties props) {
        this.runs = runs;
        this.jobs = jobs;
        runners.forEach(runner -> this.runners.put(runner.type(), runner));
        this.meters = meters;
        this.clock = clock;
        this.owner = instance.value();
        this.props = props;
        this.slots = new Semaphore(props.maxConcurrent());
    }

    /** Claims as many due runs as there are free slots and starts them. */
    public List<CompletableFuture<Void>> poll() {
        int free = slots.drainPermits();
        List<Run> claimed;
        try {
            claimed = runs.claimDue(owner, free);
        } catch (RuntimeException e) {
            slots.release(free);
            throw e;
        }
        slots.release(free - claimed.size());
        List<CompletableFuture<Void>> started = new ArrayList<>(claimed.size());
        for (Run run : claimed) {
            started.add(CompletableFuture.runAsync(
                    () -> {
                        try {
                            execute(run);
                        } finally {
                            slots.release();
                        }
                    },
                    workers));
        }
        return started;
    }

    /** Polls, then waits for the started runs. Tests use this to run the queue to the end. */
    public int drain() {
        List<CompletableFuture<Void>> started = poll();
        CompletableFuture.allOf(started.toArray(CompletableFuture[]::new)).join();
        return started.size();
    }

    /**
     * Fails runs whose instance stopped while running them. A live instance
     * ends every run by its timeout, so a run still RUNNING well past its
     * timeout has no owner left. Each such run fails and, when the job has
     * retries left, gets its next attempt. Safe on every instance at once:
     * only 1 instance can move a run out of RUNNING.
     */
    public int recoverAbandoned() {
        Instant now = Instant.now(clock);
        int recovered = 0;
        for (Run run : runs.runningSince(now.minus(ABANDON_GRACE))) {
            Optional<JobDefinition> job = jobs.findById(run.getJobId());
            Duration limit = Duration.ofSeconds(
                            job.map(JobDefinition::timeoutSeconds).orElse(0))
                    .plus(ABANDON_GRACE);
            if (run.getStartedAt().plus(limit).isAfter(now)) {
                continue;
            }
            String reason = "Abandoned: instance " + run.getOwner() + " stopped while running this.";
            if (finishAndMaybeRetry(run, job.orElse(null), RunStatus.FAILED, reason)) {
                recovered++;
            }
        }
        return recovered;
    }

    void execute(Run run) {
        Optional<JobDefinition> found = jobs.findById(run.getJobId());
        if (found.isEmpty()) {
            finish(run, null, RunStatus.FAILED, "The job was deleted before this run started.", Duration.ZERO);
            return;
        }
        JobDefinition job = found.get();
        JobRunner runner = runners.get(job.type());
        Instant started = Instant.now(clock);
        Future<String> work = workers.submit(() -> runner.run(job, run));
        RunStatus status;
        String output;
        try {
            output = work.get(job.timeoutSeconds(), TimeUnit.SECONDS);
            status = RunStatus.SUCCEEDED;
        } catch (TimeoutException e) {
            work.cancel(true);
            status = RunStatus.TIMED_OUT;
            output = "Timed out after " + job.timeoutSeconds() + " s.";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            status = RunStatus.FAILED;
            output = cause instanceof JobFailedException ? cause.getMessage() : cause.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            work.cancel(true);
            status = RunStatus.FAILED;
            output = "Interrupted while the server was stopping.";
        }
        finish(run, job, status, output, Duration.between(started, Instant.now(clock)));
    }

    private void finish(Run run, JobDefinition job, RunStatus status, String output, Duration took) {
        Timer.builder("schedkeel.run.duration")
                .description("How long runs take, by job and outcome")
                .tag("job", run.getJobName())
                .tag("status", status.name())
                .register(meters)
                .record(took);
        finishAndMaybeRetry(run, job, status, output);
    }

    /** Returns false when another instance finished the run first. */
    private boolean finishAndMaybeRetry(Run run, JobDefinition job, RunStatus status, String output) {
        Optional<Run> finished = runs.finish(run.getId(), status, truncate(output));
        if (finished.isEmpty()) {
            return false;
        }
        if (job == null || status == RunStatus.SUCCEEDED) {
            return true;
        }
        RetryPolicy policy =
                new RetryPolicy(job.maxRetries(), Duration.ofSeconds(job.retryBackoffSeconds()), props.maxBackoff());
        policy.nextAttemptAt(run.getAttempt(), finished.get().getFinishedAt()).ifPresent(at -> {
            Run retry = runs.enqueueRetry(finished.get(), at);
            log.info(
                    "Run {} of {} {}; attempt {} queued for {}.",
                    run.getId(),
                    job.name(),
                    status,
                    retry.getAttempt(),
                    at);
        });
        return true;
    }

    String truncate(String output) {
        if (output == null || output.length() <= props.maxOutput()) {
            return output;
        }
        return output.substring(0, props.maxOutput()) + "\n[output truncated]";
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
