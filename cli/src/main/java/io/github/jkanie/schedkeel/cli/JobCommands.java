package io.github.jkanie.schedkeel.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The shell commands. Each returns the text the shell prints. */
@Component
class JobCommands {

    /** Waits between 2 looks at a run, while trigger --wait follows it. Tests replace it. */
    interface Pause {
        void second();
    }

    private final SchedkeelClient client;
    private Pause pause = JobCommands::sleepOneSecond;
    private Clock clock = Clock.systemUTC();

    JobCommands(SchedkeelClient client) {
        this.client = client;
    }

    void pause(Pause pause) {
        this.pause = pause;
    }

    void clock(Clock clock) {
        this.clock = clock;
    }

    private static final DateTimeFormatter FIRE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx (EEE)", Locale.ROOT);

    @Command(name = "next", group = "Jobs", description = "Show the next fire times of a job")
    String next(
            @Argument(index = 0, description = "Job name") String name,
            @Option(longName = "count", defaultValue = "5", description = "How many, 1 to 100") int count) {
        if (count < 1 || count > 100) {
            return "Error: --count must be from 1 to 100.";
        }
        return call(() -> {
            JsonNode job = client.job(name);
            String cron = text(job, "cron");
            String zone = text(job, "timeZone");
            CronExpression expression;
            ZoneId zoneId;
            try {
                expression = CronExpression.parse(cron);
                zoneId = ZoneId.of(zone);
            } catch (IllegalArgumentException | DateTimeException e) {
                return "Error: the job's cron or zone cannot be read here: " + e.getMessage();
            }
            StringBuilder out = new StringBuilder("Next fires of " + name + " (" + cron + ", " + zone + "):\n");
            ZonedDateTime at = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
            int shown = 0;
            while (shown < count) {
                at = expression.next(at);
                if (at == null) {
                    break;
                }
                out.append(FIRE.format(at)).append('\n');
                shown++;
            }
            if (shown == 0) {
                out.append("None. The cron never fires again.\n");
            }
            if (!job.path("enabled").asBoolean(true)) {
                out.append("The job is paused, so none of these fires until resume NAME.\n");
            }
            return out.toString().stripTrailing();
        });
    }

    @Command(name = "jobs", group = "Jobs", description = "List every job, or only the jobs of 1 type")
    String jobs(@Option(longName = "type", description = "Only jobs of this type, such as HTTP") String type) {
        return call(() -> {
            Table table = new Table("NAME", "CRON", "ZONE", "TYPE", "ENABLED", "TARGET");
            boolean any = false;
            for (JsonNode job : client.jobs()) {
                any = true;
                if (type != null && !text(job, "type").equalsIgnoreCase(type)) {
                    continue;
                }
                table.row(
                        text(job, "name"),
                        text(job, "cron"),
                        text(job, "timeZone"),
                        text(job, "type"),
                        text(job, "enabled"),
                        text(job, "target"));
            }
            return table.render(
                    any && type != null
                            ? "No " + type.toUpperCase(Locale.ROOT) + " jobs."
                            : "No jobs yet. Create one with: create --name ... --cron ... --type ... --target ...");
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
            @Option(longName = "retries", description = "Retries after a failed first attempt") Integer retries,
            @Option(longName = "description", description = "What the job is for") String description) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("cron", cron);
        fields.put("type", type.toUpperCase(Locale.ROOT));
        fields.put("target", target);
        putIfSet(fields, "timeZone", zone);
        putIfSet(fields, "payload", payload);
        putIfSet(fields, "maxRetries", retries);
        putIfSet(fields, "description", description);
        return call(() -> "Created.\n" + describe(client.create(fields)));
    }

    /** The fields of a job that PUT /api/jobs/{name} takes. update copies them from the job as it is. */
    static final List<String> REQUEST_FIELDS = List.of(
            "name",
            "description",
            "cron",
            "timeZone",
            "type",
            "target",
            "payload",
            "enabled",
            "concurrency",
            "maxRetries",
            "retryBackoffSeconds",
            "timeoutSeconds");

    @Command(name = "update", group = "Jobs", description = "Change some fields of a job, keep the rest")
    String update(
            @Argument(index = 0, description = "Job name") String name,
            @Option(longName = "cron", description = "6-field cron, in quotes") String cron,
            @Option(longName = "zone", description = "Time zone, for example America/Whitehorse") String zone,
            @Option(longName = "target", description = "URL, topic or batch job name") String target,
            @Option(longName = "payload", description = "Body, message or log text") String payload,
            @Option(longName = "retries", description = "Retries after a failed first attempt") Integer retries,
            @Option(longName = "description", description = "What the job is for") String description) {
        Map<String, Object> changes = new LinkedHashMap<>();
        putIfSet(changes, "cron", cron);
        putIfSet(changes, "timeZone", zone);
        putIfSet(changes, "target", target);
        putIfSet(changes, "payload", payload);
        putIfSet(changes, "maxRetries", retries);
        putIfSet(changes, "description", description);
        if (changes.isEmpty()) {
            return "Error: give 1 or more of --cron, --zone, --target, --payload, --retries, --description.";
        }
        return call(() -> {
            Map<String, Object> fields = requestFields(client.job(name));
            fields.putAll(changes);
            return "Updated.\n" + describe(client.update(name, fields));
        });
    }

    /** The fields of job that PUT and POST /api/jobs take, in their order, leaving out nulls. */
    private static Map<String, Object> requestFields(JsonNode job) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : REQUEST_FIELDS) {
            JsonNode value = job.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                fields.put(field, value);
            }
        }
        return fields;
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Command(name = "export", group = "Jobs", description = "Print a job as JSON that import reads")
    String export(@Argument(index = 0, description = "Job name") String name) {
        return call(() -> JSON.writerWithDefaultPrettyPrinter().writeValueAsString(requestFields(client.job(name))));
    }

    @Command(name = "import", group = "Jobs", description = "Create a job from a file that export wrote")
    String importJob(@Argument(index = 0, description = "JSON file") String file) {
        JsonNode body;
        try {
            body = JSON.readTree(Files.readString(Path.of(file)));
        } catch (IOException e) {
            return "Error: cannot read " + file + ": " + e.getMessage();
        } catch (JacksonException e) {
            return "Error: " + file + " is not JSON: " + e.getOriginalMessage();
        }
        if (!body.isObject()) {
            return "Error: " + file + " must hold 1 JSON object, as export writes it.";
        }
        Map<String, Object> fields = requestFields(body);
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
            @Option(longName = "limit", defaultValue = "20", description = "How many, from 1 to 2000") int limit) {
        // The server reads a page size below 1 as its default of 20 and cuts one above 2000 down to 2000, so the
        // CLI says so instead of listing a different number of runs than was asked for.
        if (limit < 1 || limit > 2000) {
            return "Error: --limit must be from 1 to 2000.";
        }
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
