package io.github.jkanie.schedkeel.scheduling;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

/** A cron expression bound to a time zone. */
record CronSchedule(CronExpression expression, ZoneId zone) {

    static CronSchedule of(String cron, String zone) {
        return new CronSchedule(CronExpression.parse(cron), ZoneId.of(zone));
    }

    /** The first fire time strictly after {@code after}, or null when the expression never fires again. */
    Instant nextAfter(Instant after) {
        ZonedDateTime next = expression.next(after.atZone(zone));
        return next == null ? null : next.toInstant();
    }

    /**
     * The next fire time once a fire at {@code fired} is handled at {@code now}.
     * Fire times missed while the scheduler was down collapse into the run that
     * was just queued, so a long outage queues 1 run, not 1 per missed slot.
     */
    Instant nextAfterFire(Instant fired, Instant now) {
        return nextAfter(fired.isAfter(now) ? fired : now);
    }
}
