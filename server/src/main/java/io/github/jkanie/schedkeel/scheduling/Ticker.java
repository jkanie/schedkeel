package io.github.jkanie.schedkeel.scheduling;

import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.run.RunService;
import io.github.jkanie.schedkeel.run.TriggerSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Queues a run for every due schedule and moves each schedule to its next fire time. */
public class Ticker {

    private static final Logger log = LoggerFactory.getLogger(Ticker.class);

    private final ScheduleStore store;
    private final JobCatalog jobs;
    private final RunService runs;
    private final Clock clock;
    private final int batchSize;

    Ticker(ScheduleStore store, JobCatalog jobs, RunService runs, Clock clock, int batchSize) {
        this.store = store;
        this.jobs = jobs;
        this.runs = runs;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** Handles every schedule due now. Returns how many runs it queued or skipped. */
    @Transactional
    public int tick() {
        Instant now = Instant.now(clock);
        List<ScheduleStore.Due> due = store.lockDue(now, batchSize);
        for (ScheduleStore.Due schedule : due) {
            Optional<JobDefinition> job = jobs.findById(schedule.jobId());
            if (job.isEmpty()) {
                store.delete(schedule.jobId());
                continue;
            }
            runs.enqueue(job.get(), TriggerSource.SCHEDULE, schedule.nextFireAt());
            Instant next =
                    CronSchedule.of(schedule.cron(), schedule.timeZone()).nextAfterFire(schedule.nextFireAt(), now);
            if (next == null) {
                log.info(
                        "Job {} has no future fire time; its schedule is now off.",
                        job.get().name());
                store.disable(schedule.jobId());
            } else {
                store.advance(schedule.jobId(), next);
            }
        }
        return due.size();
    }
}
