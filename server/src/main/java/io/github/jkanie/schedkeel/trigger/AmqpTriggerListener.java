package io.github.jkanie.schedkeel.trigger;

import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.run.RunService;
import io.github.jkanie.schedkeel.run.TriggerSource;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/** Queues a run for each job name that arrives on the RabbitMQ trigger queue. */
@Component
class AmqpTriggerListener {

    private static final Logger log = LoggerFactory.getLogger(AmqpTriggerListener.class);

    private final JobCatalog jobs;
    private final RunService runs;
    private final Clock clock;

    AmqpTriggerListener(JobCatalog jobs, RunService runs, Clock clock) {
        this.jobs = jobs;
        this.runs = runs;
        this.clock = clock;
    }

    @RabbitListener(id = "schedkeel-amqp-triggers", queues = "${schedkeel.amqp.trigger-queue}")
    void on(String jobName) {
        String name = jobName == null ? "" : jobName.trim();
        jobs.findByName(name)
                .ifPresentOrElse(
                        job -> runs.enqueue(job, TriggerSource.AMQP, Instant.now(clock)),
                        () -> log.warn("Ignored an AMQP trigger for '{}': no job has that name.", name));
    }

    @Configuration(proxyBeanMethods = false)
    static class TriggerQueueConfiguration {

        /** Declares the durable trigger queue at start-up when it does not exist yet. */
        @Bean
        Queue amqpTriggerQueue(@Value("${schedkeel.amqp.trigger-queue}") String name) {
            return new Queue(name, true);
        }
    }
}
