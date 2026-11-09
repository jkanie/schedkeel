package io.github.jkanie.schedkeel.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Exponential back-off: base, 2 x base, 4 x base, and so on, capped at max. */
record RetryPolicy(int maxRetries, Duration base, Duration max) {

    /** When the next attempt may start, or empty once attempts are used up. */
    Optional<Instant> nextAttemptAt(int failedAttempt, Instant failedAt) {
        if (failedAttempt > maxRetries) {
            return Optional.empty();
        }
        int doublings = Math.min(failedAttempt - 1, 30);
        Duration wait = base.multipliedBy(1L << doublings);
        if (wait.compareTo(max) > 0) {
            wait = max;
        }
        return Optional.of(failedAt.plus(wait));
    }
}
