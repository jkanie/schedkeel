package io.github.jkanie.schedkeel.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

/** Calls the schedkeel API over HTTP Basic and unwraps HAL responses. */
@Component
class SchedkeelClient {

    private final RestClient http;

    SchedkeelClient(RestClient.Builder builder, CliProperties props) {
        RestClient.Builder configured = builder.baseUrl(props.url().toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/hal+json, application/problem+json");
        if (props.password() != null && !props.password().isBlank()) {
            configured.defaultHeaders(h -> h.setBasicAuth(props.user(), props.password()));
        }
        this.http = configured.build();
    }

    List<JsonNode> jobs() {
        return embedded(http.get().uri("/api/jobs").retrieve().body(JsonNode.class), "jobs");
    }

    JsonNode job(String name) {
        return http.get().uri("/api/jobs/{name}", name).retrieve().body(JsonNode.class);
    }

    JsonNode create(Map<String, Object> fields) {
        return http.post()
                .uri("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LinkedHashMap<>(fields))
                .retrieve()
                .body(JsonNode.class);
    }

    JsonNode pause(String name) {
        return http.post().uri("/api/jobs/{name}/pause", name).retrieve().body(JsonNode.class);
    }

    JsonNode resume(String name) {
        return http.post().uri("/api/jobs/{name}/resume", name).retrieve().body(JsonNode.class);
    }

    void delete(String name) {
        http.delete().uri("/api/jobs/{name}", name).retrieve().toBodilessEntity();
    }

    JsonNode trigger(String name) {
        return http.post().uri("/api/jobs/{name}/runs", name).retrieve().body(JsonNode.class);
    }

    List<JsonNode> runs(String job, String status, int limit) {
        JsonNode page = http.get()
                .uri(b -> {
                    UriBuilder u = b.path("/api/runs").queryParam("size", limit);
                    if (job != null && !job.isBlank()) {
                        u.queryParam("job", job);
                    }
                    if (status != null && !status.isBlank()) {
                        u.queryParam("status", status);
                    }
                    return u.build();
                })
                .retrieve()
                .body(JsonNode.class);
        return embedded(page, "runs");
    }

    JsonNode run(long id) {
        return http.get().uri("/api/runs/{id}", id).retrieve().body(JsonNode.class);
    }

    List<JsonNode> history(String name) {
        return embedded(
                http.get().uri("/api/jobs/{name}/history", name).retrieve().body(JsonNode.class), "revisions");
    }

    private static List<JsonNode> embedded(JsonNode body, String rel) {
        List<JsonNode> items = new ArrayList<>();
        if (body != null) {
            body.path("_embedded").path(rel).forEach(items::add);
        }
        return items;
    }
}
