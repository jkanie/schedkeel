package io.github.jkanie.schedkeel.housekeeping;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.MutableClock;
import io.github.jkanie.schedkeel.TestData;
import io.github.jkanie.schedkeel.execution.Dispatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.FileSystemUtils;

@IntegrationTest
class ArchiveRunsTests {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Path ARCHIVE = Path.of("build/test-archive");

    @Autowired
    Api api;

    @Autowired
    TestData data;

    @Autowired
    MutableClock clock;

    @Autowired
    Dispatcher dispatcher;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void reset() throws IOException {
        data.reset();
        clock.set(NOW);
        FileSystemUtils.deleteRecursively(ARCHIVE);
    }

    private void insertRun(String job, String status, Instant finishedAt) {
        jdbc.sql("""
                INSERT INTO runs (job_id, job_name, status, trigger_source, attempt, scheduled_for, not_before,
                                  started_at, finished_at)
                VALUES (0, :job, :status, 'SCHEDULE', 1, :at, :at, :at, :at)
                """)
                .param("job", job)
                .param("status", status)
                .param("at", finishedAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Test
    void archivesOldFinishedRunsToCsvThenDeletesThem() throws Exception {
        for (int i = 0; i < 7; i++) {
            insertRun("old-job", i % 2 == 0 ? "SUCCEEDED" : "FAILED", NOW.minus(Duration.ofDays(40 + i)));
        }
        insertRun("recent-job", "SUCCEEDED", NOW.minus(Duration.ofDays(2)));
        api.createJob("archive-runs", "0 0 3 * * *", "\"type\":\"BATCH\",\"target\":\"archiveRuns\"");
        api.trigger("archive-runs");

        assertThat(dispatcher.drain()).isEqualTo(1);

        String output = jdbc.sql("SELECT status || ': ' || output FROM runs WHERE job_name = 'archive-runs'")
                .query(String.class)
                .single();
        assertThat(output).startsWith("SUCCEEDED: Batch archiveRuns execution").endsWith("COMPLETED (COMPLETED)");
        assertThat(jdbc.sql("SELECT job_name FROM runs ORDER BY id")
                        .query(String.class)
                        .list())
                .containsExactly("recent-job", "archive-runs");

        List<Path> files;
        try (Stream<Path> listed = Files.list(ARCHIVE)) {
            files = listed.toList();
        }
        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.getFileName().toString()).isEqualTo("runs-before-20260130T120000Z-run-9.csv");
            List<String> lines = Files.readAllLines(file);
            assertThat(lines).hasSize(8);
            assertThat(lines.get(0)).isEqualTo("id,jobName,status,trigger,attempt,scheduledFor,startedAt,finishedAt");
            assertThat(lines.get(1))
                    .isEqualTo("1,old-job,SUCCEEDED,SCHEDULE,1,2026-01-20T12:00:00Z,"
                            + "2026-01-20T12:00:00Z,2026-01-20T12:00:00Z");
        });
    }

    @Test
    void writesAnEmptyArchiveWhenNothingIsOld() throws Exception {
        insertRun("recent-job", "SUCCEEDED", NOW.minus(Duration.ofDays(1)));
        api.createJob("archive-runs", "0 0 3 * * *", "\"type\":\"BATCH\",\"target\":\"archiveRuns\"");
        api.trigger("archive-runs");
        dispatcher.drain();

        assertThat(jdbc.sql("SELECT count(*) FROM runs").query(Long.class).single())
                .isEqualTo(2);
        try (Stream<Path> listed = Files.list(ARCHIVE)) {
            assertThat(listed.toList())
                    .singleElement()
                    .satisfies(file -> assertThat(Files.readAllLines(file)).hasSize(1));
        }
    }
}
