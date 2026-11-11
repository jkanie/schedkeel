package io.github.jkanie.schedkeel.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** The shell commands. Each returns the text the shell prints. */
@Component
class JobCommands {

    /** Waits between 2 looks at a run, while trigger --wait follows it. Tests replace it. */
    interface Pause {
        void second();
    }

    private final SchedkeelClient client;
    private Pause pause = JobCommands::sleepOneSecond;

    JobCommands(SchedkeelClient client) {
        this.client = client;
    }

    void pause(Pause pause) {
        this.pause = pause;
    }

    @Command(name = "jobs", group = "Jobs", description = "List every job")
    String jobs() {
        return call(() -> {
            Table table = new Table("NAME", "CRON", "ZONE", "TYPE", "ENABLED", "TARGET");
            for (JsonNode job : client.jobs()) {
                table.row(
                        text(job, "name"),
                        text(job, "cron"),
                        text(job, "timeZone"),
                        text(job, "type"),
                        text(job, "enabled"),
                        text(job, "target"));
            }
            return table.render("No jobs yet. Create one with: create --name ... --cron ... --type ... --target ...");
        });
    }

    @Command(name = "job", group = "Jobs", description = "Show 1 job")
    String job(@Argument(index = 0, description = "Job name") String name) {
        return call(() -> describe(client.job(name)));
    }

    @Command(name = "create", group = "Jobs", description = "Create a job")
    String create(
            @Option(longName = "name", required = true, description = "Unique job name") String name,
            @Option(longName = "cron", required = true, description = "6-field cron, in quotes") String cron,
            @Option(longName = "type", required = true, description = "LOG, HTTP, KAFKA, AMQP, PULSAR, BATCH")
                    String type,
            @Option(longName = "target", required = true, description = "URL, topic or batch job name") String target,
            @Option(longName = "zone", description = "Time zone, for example America/Whitehorse") String zone,
            @Option(longName = "payload", description = "Body, message or log text") String payload,
            @Option(longName = "retries", description = "Retries after a failed first attempt") Integer retries) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("cron", cron);
        fields.put("type", type.toUpperCase(Locale.ROOT));
        fields.put("target", target);
        putIfSet(fields, "timeZone", zone);
        putIfSet(fields, "payload", payload);
        putIfSet(fields, "maxRetries", retries);
        return call(() -> "Created.\n" + describe(client.create(fields)));
    }

    @Command(name = "trigger", group = "Runs", description = "Run a job now")
    String trigger(
            @Argument(index = 0, description = "Job name") String name,
            @Option(longName = "wait", description = "Wait up to this many seconds for the run to end, then show it")
                    Integer wait) {
        if (wait != null && wait < 1) {
            return "Error: --wait needs 1 or more seconds.";
        }
        return call(() -> {
            JsonNode run = client.trigger(name);
            String queued = "Queued run " + text(run, "id") + " of " + name + ".";
            if (wait == null) {
                return queued;
            }
            long id = run.get("id").asLong();
            String status = text(run, "status");
            for (int second = 0; second < wait; second++) {
                pause.second();
                JsonNode now = client.run(id);
                status = text(now, "status");
                if (!status.equals("QUEUED") && !status.equals("RUNNING")) {
                    return queued + "\n" + describeRun(now);
                }
            }
            return queued + "\nStill " + status + " after " + wait + " seconds. Run: schedkeel run " + id;
        });
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the run", e);
        }
    }

    @Command(name = "pause", group = "Jobs", description = "Stop a job's schedule")
    String pause(@Argument(index = 0, description = "Job name") String name) {
        return call(() -> {
            client.pause(name);
            return "Paused " + name + ".";
        });
    }

    @Command(name = "resume", group = "Jobs", description = "Start a job's schedule again")
    String resume(@Argument(index = 0, description = "Job name") String name) {
        return call(() -> {
            client.resume(name);
            return "Resumed " + name + ".";
        });
    }

    @Command(name = "delete", group = "Jobs", description = "Delete a job (its runs stay)")
    String delete(@Argument(index = 0, description = "Job name") String name) {
        return call(() -> {
            client.delete(name);
            return "Deleted " + name + ".";
        });
    }

    @Command(name = "runs", group = "Runs", description = "List recent runs, newest first")
    String runs(
            @Option(longName = "job", description = "Only runs of this job") String job,
            @Option(longName = "status", description = "Only runs with this status") String status,
            @Option(longName = "limit", defaultValue = "20", description = "How many") int limit) {
        return call(() -> {
            Table table = new Table("ID", "JOB", "STATUS", "TRIGGER", "ATTEMPT", "SCHEDULED", "FINISHED");
            for (JsonNode run : client.runs(job, status, limit)) {
                table.row(
                        text(run, "id"),
                        text(run, "jobName"),
                        text(run, "status"),
                        text(run, "trigger"),
                        text(run, "attempt"),
                        text(run, "scheduledFor"),
                        text(run, "finishedAt"));
            }
            return table.render("No runs match.");
        });
    }

    @Command(name = "run", group = "Runs", description = "Show 1 run with its output")
    String run(@Argument(index = 0, description = "Run ID") long id) {
        return call(() -> describeRun(client.run(id)));
    }

    @Command(name = "history", group = "Jobs", description = "Show every saved version of a job")
    String history(@Argument(index = 0, description = "Job name") String name) {
        return call(() -> {
            Table table = new Table("REV", "CHANGE", "WHEN", "WHO", "CRON", "ENABLED");
            List<JsonNode> revisions = client.history(name);
            for (JsonNode rev : revisions) {
                table.row(
                        text(rev, "revision"),
                        text(rev, "change"),
                        text(rev, "changedAt"),
                        text(rev, "changedBy"),
                        text(rev.path("job"), "cron"),
                        text(rev.path("job"), "enabled"));
            }
            return table.render("No history.");
        });
    }

    static String describe(JsonNode job) {
        StringBuilder out = new StringBuilder();
        for (String field : List.of(
                "name",
                "description",
                "cron",
                "timeZone",
                "type",
                "target",
                "enabled",
                "concurrency",
                "maxRetries",
                "retryBackoffSeconds",
                "timeoutSeconds",
                "updatedAt",
                "updatedBy")) {
            out.append(String.format("%-20s %s%n", field, text(job, field)));
        }
        return out.toString().stripTrailing();
    }

    /** The fields of a run, 1 per line, then its output (an error message for a failed run) below them. */
    static String describeRun(JsonNode run) {
        StringBuilder out = new StringBuilder();
        for (String field : List.of(
                "id",
                "jobName",
                "status",
                "trigger",
                "attempt",
                "scheduledFor",
                "notBefore",
                "startedAt",
                "finishedAt",
                "owner")) {
            out.append(String.format("%-20s %s%n", field, text(run, field)));
        }
        String output = text(run, "output");
        if (output.contains("\n")) {
            out.append("output\n").append(output.stripTrailing());
        } else {
            out.append(String.format("%-20s %s", "output", output));
        }
        return out.toString().stripTrailing();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "-" : value.asString();
    }

    private static void putIfSet(Map<String, Object> fields, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            fields.put(key, value);
        }
    }

    /** Turns API errors into 1 readable line instead of a stack trace. */
    private static String call(Supplier<String> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            String detail = "";
            try {
                JsonNode problem = e.getResponseBodyAs(JsonNode.class);
                if (problem != null && problem.hasNonNull("detail")) {
                    detail = ": " + problem.get("detail").asString();
                }
            } catch (RuntimeException ignored) {
                // The body was not a problem detail; the status line is enough.
            }
            return "Error " + e.getStatusCode().value() + detail;
        } catch (ResourceAccessException e) {
            return "Cannot reach the server. Check SCHEDKEEL_URL and that the server is running.";
        }
    }
}
