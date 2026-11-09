package io.github.jkanie.schedkeel.execution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpRunnerHostTests {

    private static HttpRunner runner(List<String> hosts) {
        var props = new ExecutionProperties(
                true, Duration.ofSeconds(1), 1, 100, Duration.ofHours(1), new ExecutionProperties.Http(hosts));
        return new HttpRunner(RestClient.builder(), props);
    }

    @Test
    void anEmptyListAllowsEveryHost() {
        assertThatCode(() -> runner(List.of()).checkHost(URI.create("https://example.org/x")))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAListedHostInAnyCase() {
        assertThatCode(() -> runner(List.of("Example.org")).checkHost(URI.create("https://EXAMPLE.org/x")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAnUnlistedHost() {
        assertThatThrownBy(() -> runner(List.of("example.org")).checkHost(URI.create("http://169.254.169.254/")))
                .isInstanceOf(JobFailedException.class)
                .hasMessageContaining("169.254.169.254");
    }
}
