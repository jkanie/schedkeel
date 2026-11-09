package io.github.jkanie.schedkeel.scheduling;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobDeleted;
import io.github.jkanie.schedkeel.job.JobSaved;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps job_schedule in step with job changes. Runs in the transaction that
 * changed the job, so a job and its schedule always commit together.
 */
@Component
class JobScheduleListener {

    private final ScheduleStore store;
    private final Clock clock;

    JobScheduleListener(ScheduleStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @EventListener
    void on(JobSaved event) {
        JobDefinition job = event.job();
        Optional<ScheduleStore.Current> current = store.find(job.id());
        boolean sameTimes = current.isPresent()
                && current.get().cron().equals(job.cron())
                && current.get().timeZone().equals(job.timeZone());
        boolean resumed = current.isPresent() && !current.get().enabled() && job.enabled();
        // Same cron and zone: keep the next fire time. A resumed job starts
        // from now instead, so a long pause does not fire on resume.
        if (sameTimes && !resumed) {
            store.setEnabled(job.id(), job.enabled());
            return;
        }
        Instant next = CronSchedule.of(job.cron(), job.timeZone()).nextAfter(Instant.now(clock));
        if (next == null) {
            store.delete(job.id());
            return;
        }
        store.upsert(job.id(), job.cron(), job.timeZone(), job.enabled(), next);
    }

    @EventListener
    void on(JobDeleted event) {
        store.delete(event.jobId());
    }
}
