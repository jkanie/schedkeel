package io.github.jkanie.schedkeel.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.KafkaTestConsumer;
import io.github.jkanie.schedkeel.TestData;
import io.github.jkanie.schedkeel.execution.Dispatcher;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class KafkaTests {

    @Autowired
    Api api;

    @Autowired
    TestData data;

    @Autowired
    Dispatcher dispatcher;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaConnectionDetails connection;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void reset() {
        data.reset();
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aMessageOnTheTriggerTopicQueuesARun() throws Exception {
        String job = uniqueName("on-demand");
        api.createJob(job, "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\"");

        kafka.send("schedkeel.triggers", job).get();

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbc.sql("SELECT trigger_source FROM runs WHERE job_name = :j")
                                .param("j", job)
                                .query(String.class)
                                .list())
                        .containsExactly("KAFKA"));
    }

    @Test
    void aKafkaJobPublishesItsPayload() throws Exception {
        String job = uniqueName("publish");
        String topic = "schedkeel.test-" + UUID.randomUUID();
        api.createJob(
                job, "0 0 0 1 1 *", "\"type\":\"KAFKA\",\"target\":\"" + topic + "\",\"payload\":\"{\\\"n\\\":7}\"");
        api.trigger(job);
        dispatcher.drain();

        assertThat(KafkaTestConsumer.recordsWithKey(connection, topic, job, Duration.ofSeconds(20)))
                .singleElement()
                .satisfies(r -> assertThat(r.value()).isEqualTo("{\"n\":7}"));
        assertThat(jdbc.sql("SELECT output FROM runs WHERE job_name = :j")
                        .param("j", job)
                        .query(String.class)
                        .single())
                .startsWith("Sent to " + topic + " partition ");
    }

    @Test
    void everyFinishedRunGoesToTheRunEventsTopic() throws Exception {
        String job = uniqueName("announced");
        api.createJob(job, "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\"");
        api.trigger(job);
        dispatcher.drain();

        assertThat(KafkaTestConsumer.recordsWithKey(connection, "schedkeel.run-events", job, Duration.ofSeconds(20)))
                .singleElement()
                .satisfies(r -> assertThat(r.value())
                        .contains("\"jobName\":\"" + job + "\"")
                        .contains("\"status\":\"SUCCEEDED\"")
                        .contains("\"trigger\":\"MANUAL\""));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                                jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL")
                                        .query(Long.class)
                                        .single())
                        .as("the publication registry marks the event done")
                        .isZero());
    }
}
