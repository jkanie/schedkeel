package io.github.jkanie.schedkeel.run;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.MutableClock;
import io.github.jkanie.schedkeel.TestData;
import io.github.jkanie.schedkeel.job.ConcurrencyPolicy;
import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RunQueueTests {

    private static final JobDefinition JOB = new JobDefinition(
            1, "busy", "0 * * * * *", "UTC", JobType.LOG, "x", null, true, ConcurrencyPolicy.ALLOW, 0, 30, 60);

    @Autowired
    RunService runs;

    @Autowired
    TestData data;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void reset() {
        data.reset();
        clock.set(Instant.parse("2026-03-01T12:00:00Z"));
    }

    @Test
    void manyWorkersNeverClaimTheSameRunTwice() throws Exception {
        for (int i = 0; i < 200; i++) {
            runs.enqueue(JOB, TriggerSource.MANUAL, clock.instant());
        }
        List<Callable<List<Long>>> workers = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            String owner = "worker-" + w;
            workers.add(() -> {
                List<Long> mine = new ArrayList<>();
                List<Run> batch;
                while (!(batch = runs.claimDue(owner, 7)).isEmpty()) {
                    batch.forEach(run -> mine.add(run.getId()));
                }
                return mine;
            });
        }
        List<Long> claimed = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<List<Long>> result : pool.invokeAll(workers)) {
                claimed.addAll(result.get());
            }
        }
        assertThat(claimed).hasSize(200).doesNotHaveDuplicates();
    }

    @Test
    void aRunIsNotClaimedBeforeItsNotBeforeTime() {
        Run first = runs.enqueue(JOB, TriggerSource.MANUAL, clock.instant());
        runs.claimDue("w", 1);
        runs.finish(first.getId(), RunStatus.FAILED, "boom");
        runs.enqueueRetry(
                runs.find(first.getId()).orElseThrow(), clock.instant().plusSeconds(60));

        assertThat(runs.claimDue("w", 10)).isEmpty();
        clock.set(clock.instant().plusSeconds(60));
        assertThat(runs.claimDue("w", 10)).singleElement().satisfies(run -> {
            assertThat(run.getAttempt()).isEqualTo(2);
            assertThat(run.getTriggerSource()).isEqualTo(TriggerSource.RETRY);
            assertThat(run.getOwner()).isEqualTo("w");
        });
    }

    @Test
    void finishingTwiceOnlyCountsOnce() {
        Run run = runs.enqueue(JOB, TriggerSource.MANUAL, clock.instant());
        runs.claimDue("w", 1);
        assertThat(runs.finish(run.getId(), RunStatus.SUCCEEDED, "ok")).isPresent();
        assertThat(runs.finish(run.getId(), RunStatus.FAILED, "late")).isEmpty();
        assertThat(runs.find(run.getId()).orElseThrow().getStatus()).isEqualTo(RunStatus.SUCCEEDED);
    }
}
