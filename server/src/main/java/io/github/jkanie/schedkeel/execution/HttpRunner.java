package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the job's URL. Sends a POST with the payload as the body when the job
 * has a payload, a GET otherwise. Any status outside 2xx fails the run.
 */
@Component
class HttpRunner implements JobRunner {

    private final RestClient http;
    private final List<String> allowedHosts;

    HttpRunner(RestClient.Builder builder, ExecutionProperties props) {
        this.http = builder.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
        this.allowedHosts = props.http().allowedHosts().stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .toList();
    }

    @Override
    public JobType type() {
        return JobType.HTTP;
    }

    @Override
    public String run(JobDefinition job, Run run) {
        URI uri = URI.create(job.target());
        checkHost(uri);
        ResponseEntity<String> response = job.payload() == null
                ? http.get()
                        .uri(uri)
                        .header("X-Schedkeel-Run", String.valueOf(run.getId()))
                        .retrieve()
                        .toEntity(String.class)
                : http.post()
                        .uri(uri)
                        .header("X-Schedkeel-Run", String.valueOf(run.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(job.payload())
                        .retrieve()
                        .toEntity(String.class);
        String summary = "HTTP " + response.getStatusCode().value()
                + (response.getBody() == null ? "" : "\n" + response.getBody());
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new JobFailedException(summary);
        }
        return summary;
    }

    /** An empty allow-list allows every host. */
    void checkHost(URI uri) {
        if (allowedHosts.isEmpty()) {
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new JobFailedException("Host '" + host + "' is not in schedkeel.execution.http.allowed-hosts.");
        }
    }
}
