package io.github.jkanie.schedkeel.scheduling;

import io.github.jkanie.schedkeel.InstanceId;
import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.run.RunService;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SchedulerProperties.class)
class SchedulingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfiguration.class);

    @Bean
    Ticker ticker(ScheduleStore store, JobCatalog jobs, RunService runs, Clock clock, SchedulerProperties props) {
        return new Ticker(store, jobs, runs, clock, props.batchSize());
    }

    @Bean
    LeaderLease leaderLease(StringRedisTemplate redis, InstanceId instance, SchedulerProperties props) {
        return new LeaderLease(redis, props.leaseKey(), instance.value(), props.leaseTtl());
    }

    /** Starts the tick loop. Tests turn it off and call {@link Ticker#tick()} themselves. */
    @Bean
    @ConditionalOnBooleanProperty(name = "schedkeel.scheduler.enabled", matchIfMissing = true)
    TickLoop tickLoop(Ticker ticker, LeaderLease lease, SchedulerProperties props) {
        return new TickLoop(ticker, lease, props.tick());
    }

    static final class TickLoop implements SmartLifecycle {

        private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        private final Ticker ticker;
        private final LeaderLease lease;
        private final Duration every;
        private ScheduledFuture<?> task;

        TickLoop(Ticker ticker, LeaderLease lease, Duration every) {
            this.ticker = ticker;
            this.lease = lease;
            this.every = every;
            scheduler.setThreadNamePrefix("schedkeel-tick-");
            scheduler.setPoolSize(1);
        }

        @Override
        public synchronized void start() {
            scheduler.initialize();
            task = scheduler.scheduleWithFixedDelay(this::tickIfLeader, every);
        }

        private void tickIfLeader() {
            try {
                if (lease.acquireOrRenew()) {
                    ticker.tick();
                }
            } catch (RuntimeException e) {
                log.warn("Scheduler tick failed; the next tick tries again.", e);
            }
        }

        @Override
        public synchronized void stop() {
            if (task != null) {
                task.cancel(false);
                scheduler.shutdown();
                task = null;
                lease.release();
            }
        }

        @Override
        public synchronized boolean isRunning() {
            return task != null;
        }
    }
}
