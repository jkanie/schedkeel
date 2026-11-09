package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.InstanceId;
import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.run.RunService;
import io.github.jkanie.schedkeel.run.RunStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExecutionProperties.class)
class ExecutionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExecutionConfiguration.class);

    @Bean
    Dispatcher dispatcher(
            RunService runs,
            JobCatalog jobs,
            List<JobRunner> runners,
            MeterRegistry meters,
            Clock clock,
            InstanceId instance,
            ExecutionProperties props) {
        return new Dispatcher(runs, jobs, runners, meters, clock, instance, props);
    }

    /** Exposes the queue length as the schedkeel.runs.queued gauge. */
    @Bean
    MeterBinder queuedRunsGauge(RunService runs) {
        return registry -> Gauge.builder("schedkeel.runs.queued", runs, r -> r.countByStatus(RunStatus.QUEUED))
                .description("Runs waiting to start")
                .register(registry);
    }

    /** Starts the poll loop. Tests turn it off and call {@link Dispatcher#drain()} themselves. */
    @Bean
    @ConditionalOnBooleanProperty(name = "schedkeel.execution.enabled", matchIfMissing = true)
    PollLoop pollLoop(Dispatcher dispatcher, ExecutionProperties props) {
        return new PollLoop(dispatcher, props);
    }

    static final class PollLoop implements SmartLifecycle {

        private final Dispatcher dispatcher;
        private final ExecutionProperties props;
        private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        private ScheduledFuture<?> task;
        private ScheduledFuture<?> recovery;

        PollLoop(Dispatcher dispatcher, ExecutionProperties props) {
            this.dispatcher = dispatcher;
            this.props = props;
            scheduler.setThreadNamePrefix("schedkeel-poll-");
            scheduler.setPoolSize(2);
        }

        @Override
        public synchronized void start() {
            scheduler.initialize();
            task = scheduler.scheduleWithFixedDelay(this::pollSafely, props.poll());
            recovery = scheduler.scheduleWithFixedDelay(this::recoverSafely, Duration.ofSeconds(30));
        }

        private void recoverSafely() {
            try {
                int recovered = dispatcher.recoverAbandoned();
                if (recovered > 0) {
                    log.warn("Failed {} abandoned runs.", recovered);
                }
            } catch (RuntimeException e) {
                log.warn("Abandoned-run check failed; the next check tries again.", e);
            }
        }

        private void pollSafely() {
            try {
                dispatcher.poll();
            } catch (RuntimeException e) {
                log.warn("Run poll failed; the next poll tries again.", e);
            }
        }

        @Override
        public synchronized void stop() {
            if (task != null) {
                task.cancel(false);
                recovery.cancel(false);
                scheduler.shutdown();
                task = null;
            }
        }

        @Override
        public synchronized boolean isRunning() {
            return task != null;
        }
    }
}
