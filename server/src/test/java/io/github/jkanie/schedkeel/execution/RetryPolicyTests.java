package io.github.jkanie.schedkeel.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryPolicyTests {

    private static final Instant FAILED = Instant.parse("2026-03-01T12:00:00Z");

    @Test
    void doublesTheWaitAfterEachFailure() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(10), Duration.ofHours(1));
        assertThat(policy.nextAttemptAt(1, FAILED)).contains(FAILED.plusSeconds(10));
        assertThat(policy.nextAttemptAt(2, FAILED)).contains(FAILED.plusSeconds(20));
        assertThat(policy.nextAttemptAt(3, FAILED)).contains(FAILED.plusSeconds(40));
    }

    @Test
    void stopsOnceRetriesAreUsedUp() {
        RetryPolicy policy = new RetryPolicy(2, Duration.ofSeconds(10), Duration.ofHours(1));
        assertThat(policy.nextAttemptAt(3, FAILED)).isEmpty();
    }

    @Test
    void neverRetriesWhenMaxRetriesIsZero() {
        assertThat(new RetryPolicy(0, Duration.ofSeconds(10), Duration.ofHours(1)).nextAttemptAt(1, FAILED))
                .isEmpty();
    }

    @Test
    void capsTheWait() {
        RetryPolicy policy = new RetryPolicy(10, Duration.ofMinutes(20), Duration.ofHours(1));
        assertThat(policy.nextAttemptAt(4, FAILED)).contains(FAILED.plus(Duration.ofHours(1)));
    }
}
