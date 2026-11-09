package io.github.jkanie.schedkeel.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code schedkeel.scheduler}.
 *
 * @param enabled whether this instance ticks at all
 * @param tick how often the tick runs
 * @param batchSize how many due schedules 1 tick handles
 * @param leaseKey the Redis key of the leader lease
 * @param leaseTtl how long the lease lives without renewal
 */
@ConfigurationProperties("schedkeel.scheduler")
record SchedulerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1s") Duration tick,
        @DefaultValue("100") int batchSize,
        @DefaultValue("schedkeel:scheduler:leader") String leaseKey,
        @DefaultValue("10s") Duration leaseTtl) {}
