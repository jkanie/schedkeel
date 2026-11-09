/**
 * Turns cron schedules into queued runs.
 *
 * <p>Only the instance that holds the Redis leader lease ticks. The tick
 * also locks schedule rows with {@code FOR UPDATE SKIP LOCKED}, so a lost
 * lease never leads to a double fire.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Scheduling")
package io.github.jkanie.schedkeel.scheduling;
