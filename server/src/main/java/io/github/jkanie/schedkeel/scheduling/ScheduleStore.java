package io.github.jkanie.schedkeel.scheduling;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for the job_schedule table, which only this module touches. */
@Repository
class ScheduleStore {

    record Due(long jobId, String cron, String timeZone, Instant nextFireAt) {}

    private final JdbcClient jdbc;

    ScheduleStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void upsert(long jobId, String cron, String timeZone, boolean enabled, Instant nextFireAt) {
        jdbc.sql("""
                INSERT INTO job_schedule (job_id, cron, time_zone, enabled, next_fire_at)
                VALUES (:id, :cron, :zone, :enabled, :next)
                ON CONFLICT (job_id) DO UPDATE SET
                    cron = EXCLUDED.cron, time_zone = EXCLUDED.time_zone,
                    enabled = EXCLUDED.enabled, next_fire_at = EXCLUDED.next_fire_at
                """)
                .param("id", jobId)
                .param("cron", cron)
                .param("zone", timeZone)
                .param("enabled", enabled)
                .param("next", utc(nextFireAt))
                .update();
    }

    record Current(String cron, String timeZone, boolean enabled) {}

    Optional<Current> find(long jobId) {
        return jdbc.sql("SELECT cron, time_zone, enabled FROM job_schedule WHERE job_id = :id")
                .param("id", jobId)
                .query((rs, row) ->
                        new Current(rs.getString("cron"), rs.getString("time_zone"), rs.getBoolean("enabled")))
                .optional();
    }

    void setEnabled(long jobId, boolean enabled) {
        jdbc.sql("UPDATE job_schedule SET enabled = :enabled WHERE job_id = :id")
                .param("enabled", enabled)
                .param("id", jobId)
                .update();
    }

    void delete(long jobId) {
        jdbc.sql("DELETE FROM job_schedule WHERE job_id = :id")
                .param("id", jobId)
                .update();
    }

    /** Locks and returns due schedules. Call inside a transaction. */
    List<Due> lockDue(Instant now, int limit) {
        return jdbc.sql("""
                SELECT job_id, cron, time_zone, next_fire_at FROM job_schedule
                WHERE enabled AND next_fire_at <= :now
                ORDER BY next_fire_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("now", utc(now))
                .param("limit", limit)
                .query((rs, row) -> new Due(
                        rs.getLong("job_id"),
                        rs.getString("cron"),
                        rs.getString("time_zone"),
                        rs.getObject("next_fire_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    void advance(long jobId, Instant nextFireAt) {
        jdbc.sql("UPDATE job_schedule SET next_fire_at = :next WHERE job_id = :id")
                .param("next", utc(nextFireAt))
                .param("id", jobId)
                .update();
    }

    void disable(long jobId) {
        setEnabled(jobId, false);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
