package io.github.jkanie.schedkeel.housekeeping;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The {@code archiveRuns} batch job. Step 1 writes finished runs older than
 * the retention period to a CSV file. Step 2 deletes the same runs. The
 * cut-off is fixed when the job starts, so both steps see the same set.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArchiveProperties.class)
class ArchiveRunsJobConfiguration {

    static final String JOB_NAME = "archiveRuns";

    static final String CUTOFF = "schedkeel.archive.cutoff";

    private static final String TERMINAL = "('SUCCEEDED', 'FAILED', 'TIMED_OUT', 'SKIPPED')";

    @Bean
    Job archiveRunsJob(
            JobRepository jobRepository,
            Step archiveRunsToCsv,
            Step deleteArchivedRuns,
            Clock clock,
            ArchiveProperties props) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(new CutoffListener(clock, props.retentionDays()))
                .start(archiveRunsToCsv)
                .next(deleteArchivedRuns)
                .build();
    }

    /** Fixes the cut-off once per job execution. A restart keeps the first value. */
    record CutoffListener(Clock clock, int retentionDays) implements JobExecutionListener {

        @Override
        public void beforeJob(JobExecution execution) {
            if (!execution.getExecutionContext().containsKey(CUTOFF)) {
                Instant cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
                execution.getExecutionContext().putString(CUTOFF, cutoff.toString());
            }
        }
    }

    @Bean
    Step archiveRunsToCsv(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            ArchiveProperties props,
            JdbcCursorItemReader<ArchivedRun> oldRunsReader,
            FlatFileItemWriter<ArchivedRun> archiveWriter) {
        return new StepBuilder("archiveRunsToCsv", jobRepository)
                .<ArchivedRun, ArchivedRun>chunk(props.chunkSize())
                .transactionManager(tx)
                .reader(oldRunsReader)
                .writer(archiveWriter)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<ArchivedRun> oldRunsReader(
            DataSource dataSource, @Value("#{jobExecutionContext['" + CUTOFF + "']}") String cutoff) {
        return new JdbcCursorItemReaderBuilder<ArchivedRun>()
                .name("oldRunsReader")
                .dataSource(dataSource)
                .sql("SELECT id, job_name, status, trigger_source, attempt, scheduled_for, started_at, finished_at "
                        + "FROM runs WHERE finished_at < ? AND status IN " + TERMINAL + " ORDER BY id")
                .queryArguments(Timestamp.from(Instant.parse(cutoff)))
                .rowMapper((rs, row) -> new ArchivedRun(
                        rs.getLong("id"),
                        rs.getString("job_name"),
                        rs.getString("status"),
                        rs.getString("trigger_source"),
                        rs.getInt("attempt"),
                        instant(rs.getObject("scheduled_for", OffsetDateTime.class)),
                        instant(rs.getObject("started_at", OffsetDateTime.class)),
                        instant(rs.getObject("finished_at", OffsetDateTime.class))))
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemWriter<ArchivedRun> archiveWriter(
            ArchiveProperties props,
            @Value("#{jobExecutionContext['" + CUTOFF + "']}") String cutoff,
            @Value("#{jobParameters['schedkeel.run']}") Long runId)
            throws IOException {
        Files.createDirectories(props.directory());
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .format(Instant.parse(cutoff).atOffset(ZoneOffset.UTC));
        String suffix = runId == null ? "" : "-run-" + runId;
        var file = props.directory().resolve("runs-before-" + stamp + suffix + ".csv");
        return new FlatFileItemWriterBuilder<ArchivedRun>()
                .name("archiveWriter")
                .resource(new FileSystemResource(file))
                .headerCallback(
                        writer -> writer.write("id,jobName,status,trigger,attempt,scheduledFor,startedAt,finishedAt"))
                .delimited()
                .sourceType(ArchivedRun.class)
                .names("id", "jobName", "status", "trigger", "attempt", "scheduledFor", "startedAt", "finishedAt")
                .build();
    }

    @Bean
    Step deleteArchivedRuns(JobRepository jobRepository, PlatformTransactionManager tx, JdbcClient jdbc) {
        return new StepBuilder("deleteArchivedRuns", jobRepository)
                .tasklet(
                        (contribution, context) -> {
                            Instant cutoff = Instant.parse((String) context.getStepContext()
                                    .getJobExecutionContext()
                                    .get(CUTOFF));
                            int deleted = jdbc.sql(
                                            "DELETE FROM runs WHERE finished_at < :cutoff AND status IN " + TERMINAL)
                                    .param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
                                    .update();
                            contribution.incrementWriteCount(deleted);
                            return RepeatStatus.FINISHED;
                        },
                        tx)
                .build();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
