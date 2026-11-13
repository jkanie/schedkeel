package io.github.jkanie.schedkeel.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JobCommandsTests {

    private static final MediaType HAL = MediaType.parseMediaType("application/hal+json");

    private MockRestServiceServer server;
    private JobCommands commands;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        commands = new JobCommands(
                new SchedkeelClient(builder, new CliProperties(URI.create("http://sk.test"), "admin", "secret")));
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    @Test
    void listsJobsAsATable() {
        server.expect(requestTo("http://sk.test/api/jobs"))
                .andExpect(header("Authorization", "Basic YWRtaW46c2VjcmV0"))
                .andRespond(withSuccess("""
                        {"_embedded": {"jobs": [
                          {"name": "nightly-report", "cron": "0 0 2 * * *", "timeZone": "UTC",
                           "type": "HTTP", "enabled": true, "target": "https://example.org"}]}}
                        """, HAL));

        assertThat(commands.jobs(null)).isEqualTo("""
                NAME            CRON         ZONE  TYPE  ENABLED  TARGET
                nightly-report  0 0 2 * * *  UTC   HTTP  true     https://example.org""");
    }

    @Test
    void listsOnlyTheJobsOfATypeInAnyCase() {
        String twoJobs = """
                {"_embedded": {"jobs": [
                  {"name": "archive-runs", "cron": "0 0 3 * * *", "timeZone": "UTC",
                   "type": "BATCH", "enabled": true, "target": "archiveRuns"},
                  {"name": "health-check", "cron": "0 * * * * *", "timeZone": "UTC",
                   "type": "HTTP", "enabled": false, "target": "http://localhost:8080/actuator/health"}]}}
                """;
        server.expect(requestTo("http://sk.test/api/jobs")).andRespond(withSuccess(twoJobs, HAL));
        server.expect(requestTo("http://sk.test/api/jobs")).andRespond(withSuccess(twoJobs, HAL));

        assertThat(commands.jobs("http")).isEqualTo("""
                NAME          CRON         ZONE  TYPE  ENABLED  TARGET
                health-check  0 * * * * *  UTC   HTTP  false    http://localhost:8080/actuator/health""");
        assertThat(commands.jobs("kafka")).isEqualTo("No KAFKA jobs.");
    }

    @Test
    void refusesARunsLimitTheServerWouldChange() {
        // No request reaches the server: the expectations stay empty and verify() passes.
        assertThat(commands.runs(null, null, 0)).isEqualTo("Error: --limit must be from 1 to 2000.");
        assertThat(commands.runs(null, null, -5)).isEqualTo("Error: --limit must be from 1 to 2000.");
        assertThat(commands.runs(null, null, 2001)).isEqualTo("Error: --limit must be from 1 to 2000.");
        server.verify();
    }

    @Test
    void saysSoWhenThereAreNoJobs() {
        server.expect(requestTo("http://sk.test/api/jobs")).andRespond(withSuccess("{}", HAL));
        assertThat(commands.jobs(null)).startsWith("No jobs yet.");
    }

    @Test
    void nextShowsFireTimesInTheJobsZoneAcrossAClockChange() {
        // 2027-03-14 01:30 in New York, 30 minutes before clocks go forward 1 hour,
        // so 02:15 does not happen that day.
        commands.clock(Clock.fixed(Instant.parse("2027-03-14T06:30:00Z"), ZoneOffset.UTC));
        server.expect(requestTo("http://sk.test/api/jobs/hourly")).andRespond(withSuccess("""
                        {"name": "hourly", "cron": "0 15 * * * *", "timeZone": "America/New_York", "enabled": false}
                        """, HAL));

        assertThat(commands.next("hourly", 3)).isEqualTo("""
                Next fires of hourly (0 15 * * * *, America/New_York):
                2027-03-14 03:15:00 -04:00 (Sun)
                2027-03-14 04:15:00 -04:00 (Sun)
                2027-03-14 05:15:00 -04:00 (Sun)
                The job is paused, so none of these fires until resume NAME.""");
    }

    @Test
    void nextChecksTheCountAndReportsAMissingJob() {
        assertThat(commands.next("ping", 0)).isEqualTo("Error: --count must be from 1 to 100.");
        assertThat(commands.next("ping", 101)).isEqualTo("Error: --count must be from 1 to 100.");
        server.expect(requestTo("http://sk.test/api/jobs/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\": \"No job is named 'missing'.\"}"));
        assertThat(commands.next("missing", 5)).isEqualTo("Error 404: No job is named 'missing'.");
    }

    @Test
    void nextSaysSoWhenTheCronNeverFires() {
        // 30 February never comes.
        server.expect(requestTo("http://sk.test/api/jobs/never")).andRespond(withSuccess("""
                        {"name": "never", "cron": "0 0 0 30 2 *", "timeZone": "UTC", "enabled": true}
                        """, HAL));
        assertThat(commands.next("never", 5))
                .isEqualTo("Next fires of never (0 0 0 30 2 *, UTC):\nNone. The cron never fires again.");
    }

    private static final String PING = """
            {"name": "ping", "description": null, "cron": "0 * * * * *", "timeZone": "UTC",
             "type": "HTTP", "target": "https://example.org", "payload": "{}", "enabled": false,
             "concurrency": "ALLOW", "maxRetries": 2, "retryBackoffSeconds": 60, "timeoutSeconds": 90,
             "createdAt": "2027-01-01T00:00:00Z", "createdBy": "admin",
             "_links": {"self": {"href": "http://sk.test/api/jobs/ping"}}}
            """;

    @Test
    void exportWritesWhatImportCreatesAgain(@TempDir Path dir) throws IOException {
        // MockRestServiceServer takes every expectation before the 1st call.
        server.expect(requestTo("http://sk.test/api/jobs/ping")).andRespond(withSuccess(PING, HAL));
        server.expect(requestTo("http://sk.test/api/jobs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"name": "ping-copy", "cron": "0 * * * * *", "timeZone": "UTC", "type": "HTTP",
                         "target": "https://example.org", "payload": "{}", "enabled": false, "concurrency": "ALLOW",
                         "maxRetries": 2, "retryBackoffSeconds": 60, "timeoutSeconds": 90}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"name\": \"ping-copy\"}", HAL));
        String exported = commands.export("ping");
        assertThat(exported)
                .startsWith("{\n  \"name\" : \"ping\",\n  \"cron\"")
                .doesNotContain("createdAt", "_links", "description");

        Path file = dir.resolve("ping.json");
        Files.writeString(file, exported.replace("\"ping\"", "\"ping-copy\""));
        assertThat(commands.importJob(file.toString())).startsWith("Created.");
    }

    @Test
    void importReportsABadFile(@TempDir Path dir) throws IOException {
        Path missing = dir.resolve("missing.json");
        assertThat(commands.importJob(missing.toString())).startsWith("Error: cannot read " + missing);
        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{\"name\": ");
        assertThat(commands.importJob(broken.toString())).startsWith("Error: " + broken + " is not JSON: ");
        Path list = dir.resolve("list.json");
        Files.writeString(list, "[]");
        assertThat(commands.importJob(list.toString()))
                .isEqualTo("Error: " + list + " must hold 1 JSON object, as export writes it.");
    }

    @Test
    void updateChangesOnlyTheFieldsGivenAndKeepsTheRest() {
        server.expect(requestTo("http://sk.test/api/jobs/ping"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"name": "ping", "description": null, "cron": "0 * * * * *", "timeZone": "UTC",
                         "type": "HTTP", "target": "https://example.org", "payload": "{}", "enabled": false,
                         "concurrency": "ALLOW", "maxRetries": 2, "retryBackoffSeconds": 60, "timeoutSeconds": 90,
                         "createdAt": "2027-01-01T00:00:00Z", "createdBy": "admin",
                         "updatedAt": "2027-01-02T00:00:00Z", "updatedBy": "admin",
                         "_links": {"self": {"href": "http://sk.test/api/jobs/ping"}}}
                        """, HAL));
        server.expect(requestTo("http://sk.test/api/jobs/ping"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"name": "ping", "cron": "0 */5 * * * *", "timeZone": "America/Whitehorse",
                         "type": "HTTP", "target": "https://example.org", "payload": "{}", "enabled": false,
                         "concurrency": "ALLOW", "maxRetries": 0, "retryBackoffSeconds": 60, "timeoutSeconds": 90}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"name\": \"ping\", \"cron\": \"0 */5 * * * *\"}", HAL));

        String out = commands.update("ping", "0 */5 * * * *", "America/Whitehorse", null, "", 0, null);
        assertThat(out).startsWith("Updated.").contains("cron                 0 */5 * * * *");
    }

    @Test
    void updateNeedsAChangeAndReportsAMissingJob() {
        assertThat(commands.update("ping", null, " ", null, null, null, ""))
                .isEqualTo("Error: give 1 or more of --cron, --zone, --target, --payload, --retries, --description.");

        server.expect(requestTo("http://sk.test/api/jobs/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\": \"No job is named 'missing'.\"}"));
        assertThat(commands.update("missing", "0 0 * * * *", null, null, null, null, null))
                .isEqualTo("Error 404: No job is named 'missing'.");
    }

    @Test
    void createsAJobWithOnlyTheFieldsGiven() {
        server.expect(requestTo("http://sk.test/api/jobs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"name": "ping", "cron": "0 * * * * *", "type": "HTTP",
                         "target": "https://example.org", "maxRetries": 2}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(HAL)
                        .body("{\"name\": \"ping\", \"cron\": \"0 * * * * *\", \"enabled\": true}"));

        String out = commands.create("ping", "0 * * * * *", "http", "https://example.org", null, "", 2);
        assertThat(out).startsWith("Created.").contains("name                 ping");
    }

    @Test
    void triggersARun() {
        server.expect(requestTo("http://sk.test/api/jobs/ping/runs"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(HAL).body("{\"id\": 42}"));
        assertThat(commands.trigger("ping", null)).isEqualTo("Queued run 42 of ping.");
    }

    @Test
    void filtersRuns() {
        server.expect(requestTo("http://sk.test/api/runs?size=5&job=ping&status=FAILED"))
                .andRespond(withSuccess("""
                        {"_embedded": {"runs": [{"id": 7, "jobName": "ping", "status": "FAILED",
                          "trigger": "SCHEDULE", "attempt": 1, "scheduledFor": "2026-03-01T12:00:00Z",
                          "finishedAt": null}]}}
                        """, HAL));
        assertThat(commands.runs("ping", "FAILED", 5))
                .contains("7   ping  FAILED  SCHEDULE  1        2026-03-01T12:00:00Z  -");
    }

    @Test
    void turnsAProblemDetailIntoOneLine() {
        server.expect(requestTo("http://sk.test/api/jobs/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\": 404, \"detail\": \"No job is named 'missing'.\"}"));
        assertThat(commands.job("missing")).isEqualTo("Error 404: No job is named 'missing'.");
    }

    @Test
    void saysSoWhenTheServerIsDown() {
        server.expect(requestTo("http://sk.test/api/jobs")).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });
        assertThat(commands.jobs(null)).startsWith("Cannot reach the server.");
    }

    @Test
    void showsHistoryNewestFirst() {
        server.expect(requestTo("http://sk.test/api/jobs/ping/history")).andRespond(withSuccess("""
                        {"_embedded": {"revisions": [
                          {"revision": 2, "change": "UPDATE", "changedAt": "2026-03-02T00:00:00Z", "changedBy": "admin",
                           "job": {"cron": "0 0 * * * *", "enabled": false}},
                          {"revision": 1, "change": "INSERT", "changedAt": "2026-03-01T00:00:00Z", "changedBy": "admin",
                           "job": {"cron": "0 * * * * *", "enabled": true}}]}}
                        """, HAL));
        assertThat(commands.history("ping").lines())
                .hasSize(3)
                .element(1)
                .asString()
                .startsWith("2    UPDATE");
    }

    @Test
    void showsARunWithItsOutput() {
        server.expect(requestTo("http://sk.test/api/runs/7")).andRespond(withSuccess("""
                        {"id": 7, "jobName": "ping", "status": "FAILED", "trigger": "SCHEDULE", "attempt": 2,
                         "scheduledFor": "2026-03-01T12:00:00Z", "notBefore": "2026-03-01T12:00:30Z",
                         "startedAt": "2026-03-01T12:00:31Z", "finishedAt": "2026-03-01T12:00:32Z",
                         "owner": "node-a", "output": "HTTP 503 from https://example.org\\nRetry-After: 60\\n"}
                        """, HAL));
        assertThat(commands.run(7)).isEqualTo("""
                id                   7
                jobName              ping
                status               FAILED
                trigger              SCHEDULE
                attempt              2
                scheduledFor         2026-03-01T12:00:00Z
                notBefore            2026-03-01T12:00:30Z
                startedAt            2026-03-01T12:00:31Z
                finishedAt           2026-03-01T12:00:32Z
                owner                node-a
                output
                HTTP 503 from https://example.org
                Retry-After: 60""");
    }

    @Test
    void showsAQueuedRunWithDashes() {
        server.expect(requestTo("http://sk.test/api/runs/8")).andRespond(withSuccess("""
                        {"id": 8, "jobName": "ping", "status": "QUEUED", "trigger": "MANUAL", "attempt": 1,
                         "scheduledFor": "2026-03-01T12:00:00Z", "notBefore": "2026-03-01T12:00:00Z",
                         "startedAt": null, "finishedAt": null, "owner": null, "output": null}
                        """, HAL));
        assertThat(commands.run(8).lines())
                .contains("startedAt            -", "owner                -", "output               -");
    }

    @Test
    void saysSoWhenARunDoesNotExist() {
        server.expect(requestTo("http://sk.test/api/runs/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\": 404, \"detail\": \"No run has the id 99.\"}"));
        assertThat(commands.run(99)).isEqualTo("Error 404: No run has the id 99.");
    }

    @Test
    void triggerWaitsForTheRunToEnd() {
        int[] pauses = {0};
        commands.pause(() -> pauses[0]++);
        server.expect(requestTo("http://sk.test/api/jobs/ping/runs"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(HAL).body("""
                        {"id": 42, "status": "QUEUED"}"""));
        server.expect(requestTo("http://sk.test/api/runs/42")).andRespond(withSuccess("""
                        {"id": 42, "status": "RUNNING"}""", HAL));
        server.expect(requestTo("http://sk.test/api/runs/42")).andRespond(withSuccess("""
                        {"id": 42, "jobName": "ping", "status": "SUCCEEDED", "trigger": "MANUAL", "attempt": 1,
                         "output": "HTTP 200"}""", HAL));
        String out = commands.trigger("ping", 30);
        assertThat(out.lines()).startsWith("Queued run 42 of ping.", "id                   42");
        assertThat(out.lines()).contains("status               SUCCEEDED", "output               HTTP 200");
        assertThat(pauses[0]).isEqualTo(2);
    }

    @Test
    void triggerStopsWaitingAtTheLimit() {
        commands.pause(() -> {});
        server.expect(requestTo("http://sk.test/api/jobs/ping/runs"))
                .andRespond(
                        withStatus(HttpStatus.CREATED).contentType(HAL).body("{\"id\": 42, \"status\": \"QUEUED\"}"));
        server.expect(requestTo("http://sk.test/api/runs/42"))
                .andRespond(withSuccess("{\"id\": 42, \"status\": \"QUEUED\"}", HAL));
        server.expect(requestTo("http://sk.test/api/runs/42"))
                .andRespond(withSuccess("{\"id\": 42, \"status\": \"RUNNING\"}", HAL));
        assertThat(commands.trigger("ping", 2))
                .isEqualTo("Queued run 42 of ping.\nStill RUNNING after 2 seconds. Run: schedkeel run 42");
    }

    @Test
    void triggerRefusesAWaitBelow1() {
        assertThat(commands.trigger("ping", 0)).isEqualTo("Error: --wait needs 1 or more seconds.");
    }
}
