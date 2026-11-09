package io.github.jkanie.schedkeel.execution;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code schedkeel.execution}.
 *
 * @param enabled whether this instance claims and executes runs
 * @param poll how often the dispatcher looks for due runs
 * @param maxConcurrent how many runs this instance executes at once
 * @param maxOutput how many characters of output a run keeps
 * @param maxBackoff the longest wait between retries
 * @param http settings for HTTP jobs
 */
@ConfigurationProperties("schedkeel.execution")
record ExecutionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500ms") Duration poll,
        @DefaultValue("16") int maxConcurrent,
        @DefaultValue("4000") int maxOutput,
        @DefaultValue("1h") Duration maxBackoff,
        @DefaultValue Http http) {

    /** @param allowedHosts hosts HTTP jobs may call; empty allows every host */
    record Http(@DefaultValue({}) List<String> allowedHosts) {}
}
