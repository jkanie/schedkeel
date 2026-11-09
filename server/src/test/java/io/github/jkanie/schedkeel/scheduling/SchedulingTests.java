package io.github.jkanie.schedkeel.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.MutableClock;
import io.github.jkanie.schedkeel.TestData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SchedulingTests {

    private static final Instant NOON = Instant.parse("2026-03-01T12:00:00Z");

    @Autowired
    Ticker ticker;

    @Autowired
    MutableClock clock;

    @Autowired
    TestData data;

    @Autowired
    Api api;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void reset() {
        data.reset();
        clock.set(NOON);
    }

    private List<Map<String, Object>> runs() {
        return jdbc.sql("SELECT status, trigger_source, scheduled_for, output FROM runs ORDER BY id")
                .query()
                .listOfRows();
    }

    private Instant nextFire(String job) {
        return jdbc.sql("SELECT s.next_fire_at FROM job_schedule s JOIN jobs j ON j.id = s.job_id WHERE j.name = :n")
                .param("n", job)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    @Test
    void queuesARunWhenTheJobIsDue() throws Exception {
        api.createJob("every-minute", "0 * * * * *", "\"type\":\"LOG\",\"target\":\"tick\"");
        assertThat(nextFire("every-minute")).isEqualTo(Instant.parse("2026-03-01T12:01:00Z"));

        clock.set(Instant.parse("2026-03-01T12:00:59Z"));
        assertThat(ticker.tick()).isZero();

        clock.set(Instant.parse("2026-03-01T12:01:00Z"));
        assertThat(ticker.tick()).isEqualTo(1);
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run).containsEntry("status", "QUEUED").containsEntry("trigger_source", "SCHEDULE");
            assertThat(((Timestamp) run.get("scheduled_for")).toInstant())
                    .isEqualTo(Instant.parse("2026-03-01T12:01:00Z"));
        });
        assertThat(nextFire("every-minute")).isEqualTo(Instant.parse("2026-03-01T12:02:00Z"));
    }

    @Test
    void anOutageQueuesOneRunNotOnePerMissedMinute() throws Exception {
        api.createJob("every-minute", "0 * * * * *", "\"type\":\"LOG\",\"target\":\"tick\",\"concurrency\":\"ALLOW\"");
        clock.set(Instant.parse("2026-03-01T12:45:10Z"));

        assertThat(ticker.tick()).isEqualTo(1);
        assertThat(ticker.tick()).isZero();
        assertThat(runs()).hasSize(1);
        assertThat(nextFire("every-minute")).isEqualTo(Instant.parse("2026-03-01T12:46:00Z"));
    }

    @Test
    void forbidSkipsARunWhileTheLastOneIsStillQueued() throws Exception {
        api.createJob("every-minute", "0 * * * * *", "\"type\":\"LOG\",\"target\":\"tick\"");
        clock.set(Instant.parse("2026-03-01T12:01:00Z"));
        ticker.tick();
        clock.set(Instant.parse("2026-03-01T12:02:00Z"));
        ticker.tick();

        assertThat(runs()).extracting(r -> r.get("status")).containsExactly("QUEUED", "SKIPPED");
        assertThat(runs().get(1).get("output").toString()).contains("still queued or running");
    }

    @Test
    void aPausedJobDoesNotFireAndResumesFromNow() throws Exception {
        api.createJob("every-minute", "0 * * * * *", "\"type\":\"LOG\",\"target\":\"tick\"");
        mvc.perform(post("/api/jobs/every-minute/pause").with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)));

        clock.set(Instant.parse("2026-03-01T15:30:20Z"));
        assertThat(ticker.tick()).isZero();

        mvc.perform(post("/api/jobs/every-minute/resume").with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)));
        assertThat(ticker.tick()).isZero();
        assertThat(nextFire("every-minute")).isEqualTo(Instant.parse("2026-03-01T15:31:00Z"));
    }

    @Test
    void changingTheCronMovesTheNextFire() throws Exception {
        api.createJob("nightly", "0 0 2 * * *", "\"type\":\"LOG\",\"target\":\"x\"");
        assertThat(nextFire("nightly")).isEqualTo(Instant.parse("2026-03-02T02:00:00Z"));

        mvc.perform(put("/api/jobs/nightly")
                .with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD))
                .contentType("application/json")
                .content("{\"name\":\"nightly\",\"cron\":\"0 0 13 * * *\",\"type\":\"LOG\",\"target\":\"x\"}"));
        assertThat(nextFire("nightly")).isEqualTo(Instant.parse("2026-03-01T13:00:00Z"));
    }
}
