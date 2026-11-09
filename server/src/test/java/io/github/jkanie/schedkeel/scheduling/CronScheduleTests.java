package io.github.jkanie.schedkeel.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CronScheduleTests {

    @Test
    void firesAtTheNextMatchingSecondInItsZone() {
        CronSchedule daily = CronSchedule.of("0 30 2 * * *", "America/Whitehorse");
        // 2:30 in Whitehorse (UTC-7 all year) is 09:30 UTC.
        assertThat(daily.nextAfter(Instant.parse("2026-03-01T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-02T09:30:00Z"));
    }

    @Test
    void skipsALocalTimeThatDaylightSavingRemoves() {
        // Toronto jumps from 02:00 to 03:00 on 2026-03-08, so 02:30 does not exist that day.
        CronSchedule daily = CronSchedule.of("0 30 2 * * *", "America/Toronto");
        Instant next = daily.nextAfter(Instant.parse("2026-03-07T12:00:00Z"));
        assertThat(next).isEqualTo(Instant.parse("2026-03-09T06:30:00Z"));
    }

    @Test
    void collapsesMissedFiresIntoOneRun() {
        CronSchedule everyMinute = CronSchedule.of("0 * * * * *", "UTC");
        Instant fired = Instant.parse("2026-03-01T12:00:00Z");
        Instant now = Instant.parse("2026-03-01T12:42:10Z");
        assertThat(everyMinute.nextAfterFire(fired, now)).isEqualTo(Instant.parse("2026-03-01T12:43:00Z"));
    }

    @Test
    void keepsTheNormalNextFireWhenOnTime() {
        CronSchedule everyMinute = CronSchedule.of("0 * * * * *", "UTC");
        Instant fired = Instant.parse("2026-03-01T12:00:00Z");
        assertThat(everyMinute.nextAfterFire(fired, fired)).isEqualTo(Instant.parse("2026-03-01T12:01:00Z"));
    }

    @Test
    void returnsNullWhenTheExpressionNeverFiresAgain() {
        CronSchedule leapDayOnly = CronSchedule.of("0 0 0 30 2 *", "UTC");
        assertThat(leapDayOnly.nextAfter(Instant.parse("2026-03-01T12:00:00Z"))).isNull();
    }
}
