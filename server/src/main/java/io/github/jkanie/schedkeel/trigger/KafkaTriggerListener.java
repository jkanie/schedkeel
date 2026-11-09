package io.github.jkanie.schedkeel.trigger;

import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.run.RunService;
import io.github.jkanie.schedkeel.run.TriggerSource;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Queues a run for each job name that arrives on the trigger topic. */
@Component
class KafkaTriggerListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaTriggerListener.class);

    private final JobCatalog jobs;
    private final RunService runs;
    private final Clock clock;

    KafkaTriggerListener(JobCatalog jobs, RunService runs, Clock clock) {
        this.jobs = jobs;
        this.runs = runs;
        this.clock = clock;
    }

    @KafkaListener(id = "schedkeel-triggers", topics = "${schedkeel.kafka.trigger-topic}")
    void on(String jobName) {
        String name = jobName == null ? "" : jobName.trim();
        jobs.findByName(name)
                .ifPresentOrElse(
                        job -> runs.enqueue(job, TriggerSource.KAFKA, Instant.now(clock)),
                        () -> log.warn("Ignored a trigger for '{}': no job has that name.", name));
    }
}
