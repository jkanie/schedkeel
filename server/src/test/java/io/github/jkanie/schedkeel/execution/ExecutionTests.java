package io.github.jkanie.schedkeel.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.MutableClock;
import io.github.jkanie.schedkeel.TestData;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ExecutionTests {

    @Autowired
    Dispatcher dispatcher;

    @Autowired
    Api api;

    @Autowired
    TestData data;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private HttpServer server;
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final Queue<String> requests = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws Exception {
        data.reset();
        clock.set(Instant.parse("2026-03-01T12:00:00Z"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/flaky", exchange -> {
            requests.add(exchange.getRequestMethod() + " "
                    + exchange.getRequestHeaders().getFirst("X-Schedkeel-Run") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean fail = failuresLeft.getAndDecrement() > 0;
            byte[] body = (fail ? "boom" : "done").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(fail ? 500 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private List<Map<String, Object>> runs() {
        return jdbc.sql(
                        "SELECT id, status, trigger_source, attempt, not_before, finished_at, owner, output FROM runs ORDER BY id")
                .query()
                .listOfRows();
    }

    @Test
    void runsALogJob() throws Exception {
        api.createJob("hello", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\",\"payload\":\"hello world\"");
        api.trigger("hello");

        assertThat(dispatcher.drain()).isEqualTo(1);
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run)
                        .containsEntry("status", "SUCCEEDED")
                        .containsEntry("owner", "test-instance")
                        .containsEntry("output", "hello world"));
        assertThat(dispatcher.drain()).as("nothing left to claim").isZero();
    }

    @Test
    void retriesAFailedHttpCallWithBackOff() throws Exception {
        failuresLeft.set(1);
        api.createJob(
                "flaky",
                "0 0 0 1 1 *",
                "\"type\":\"HTTP\",\"target\":\"" + url("/flaky")
                        + "\",\"payload\":\"{\\\"a\\\":1}\",\"maxRetries\":2,\"retryBackoffSeconds\":10");
        api.trigger("flaky");

        dispatcher.drain();
        assertThat(runs()).extracting(r -> r.get("status")).containsExactly("FAILED", "QUEUED");
        assertThat(runs().get(0).get("output").toString())
                .startsWith("HTTP 500")
                .contains("boom");
        Map<String, Object> retry = runs().get(1);
        assertThat(retry).containsEntry("trigger_source", "RETRY").containsEntry("attempt", 2);
        assertThat(((Timestamp) retry.get("not_before")).toInstant()).isEqualTo(Instant.parse("2026-03-01T12:00:10Z"));

        assertThat(dispatcher.drain()).as("the retry waits for its back-off").isZero();
        clock.advance(Duration.ofSeconds(10));
        assertThat(dispatcher.drain()).isEqualTo(1);

        assertThat(runs()).extracting(r -> r.get("status")).containsExactly("FAILED", "SUCCEEDED");
        assertThat(requests).containsExactly("POST 1 {\"a\":1}", "POST 2 {\"a\":1}");
    }

    @Test
    void stopsRetryingAfterMaxRetries() throws Exception {
        failuresLeft.set(10);
        api.createJob(
                "flaky",
                "0 0 0 1 1 *",
                "\"type\":\"HTTP\",\"target\":\"" + url("/flaky") + "\",\"maxRetries\":1,\"retryBackoffSeconds\":1");
        api.trigger("flaky");
        dispatcher.drain();
        clock.advance(Duration.ofSeconds(1));
        dispatcher.drain();
        clock.advance(Duration.ofHours(1));

        assertThat(dispatcher.drain()).isZero();
        assertThat(runs()).extracting(r -> r.get("status")).containsExactly("FAILED", "FAILED");
        assertThat(requests).hasSize(2).allMatch(r -> r.startsWith("GET "));
    }

    @Test
    void timesOutASlowJob() throws Exception {
        api.createJob(
                "slow", "0 0 0 1 1 *", "\"type\":\"HTTP\",\"target\":\"" + url("/slow") + "\",\"timeoutSeconds\":1");
        api.trigger("slow");

        dispatcher.drain();
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run)
                        .containsEntry("status", "TIMED_OUT")
                        .containsEntry("output", "Timed out after 1 s."));
    }

    @Test
    void failsARunWhoseJobWasDeleted() throws Exception {
        api.createJob("gone", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\"");
        api.trigger("gone");
        mvc.perform(delete("/api/jobs/gone").with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)));

        dispatcher.drain();
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run)
                        .containsEntry("status", "FAILED")
                        .containsEntry("output", "The job was deleted before this run started."));
    }

    @Test
    void refusesAnUnknownBatchJob() throws Exception {
        api.createJob("bad-batch", "0 0 0 1 1 *", "\"type\":\"BATCH\",\"target\":\"noSuchJob\"");
        api.trigger("bad-batch");

        dispatcher.drain();
        assertThat(runs().get(0).get("output").toString())
                .startsWith("No Spring Batch job is named 'noSuchJob'.")
                .contains("archiveRuns");
    }

    @Test
    void recordsRunDurationsForPrometheus() throws Exception {
        api.createJob("hello", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\"");
        api.trigger("hello");
        dispatcher.drain();

        mvc.perform(get("/actuator/prometheus").with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(Matchers.containsString(
                                "schedkeel_run_duration_seconds_count{job=\"hello\",status=\"SUCCEEDED\"}")))
                .andExpect(content().string(Matchers.containsString("schedkeel_runs_queued")));
        mvc.perform(get("/actuator/prometheus").with(httpBasic(TestData.VIEWER, TestData.VIEWER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void failsAndRetriesARunWhoseInstanceStopped() throws Exception {
        api.createJob(
                "orphan", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\",\"timeoutSeconds\":60,\"maxRetries\":1");
        api.trigger("orphan");
        api.trigger("orphan");
        // Another instance claims both runs, then stops without finishing them.
        jdbc.sql("UPDATE runs SET status = 'RUNNING', started_at = :at, owner = 'dead-instance'")
                .param("at", clock.instant().atOffset(ZoneOffset.UTC))
                .update();

        clock.advance(Duration.ofSeconds(60 + 59));
        assertThat(dispatcher.recoverAbandoned())
                .as("still inside timeout plus grace")
                .isZero();

        clock.advance(Duration.ofSeconds(2));
        assertThat(dispatcher.recoverAbandoned()).isEqualTo(2);
        assertThat(runs())
                .extracting(r -> r.get("status") + " " + r.get("trigger_source") + " " + r.get("attempt"))
                .containsExactly("FAILED MANUAL 1", "FAILED MANUAL 1", "QUEUED RETRY 2", "QUEUED RETRY 2");
        assertThat(runs().get(0).get("output"))
                .isEqualTo("Abandoned: instance dead-instance stopped while running this.");
        assertThat(dispatcher.recoverAbandoned())
                .as("each run is recovered once")
                .isZero();
    }

    @Test
    void truncatesLongOutput() {
        String longText = "x".repeat(5000);
        assertThat(dispatcher.truncate(longText)).hasSize(4000 + "\n[output truncated]".length());
        assertThat(dispatcher.truncate("short")).isEqualTo("short");
    }
}
