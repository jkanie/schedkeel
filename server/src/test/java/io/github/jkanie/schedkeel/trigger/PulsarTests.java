package io.github.jkanie.schedkeel.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.TestData;
import io.github.jkanie.schedkeel.execution.Dispatcher;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PULSAR jobs against Apache Pulsar 4.2.5 in a container. */
@IntegrationTest
class PulsarTests {

    @Autowired
    Api api;

    @Autowired
    TestData data;

    @Autowired
    Dispatcher dispatcher;

    @Autowired
    PulsarClient client;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void reset() {
        data.reset();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String runOutput(String job) {
        return jdbc.sql("SELECT status || ': ' || output FROM runs WHERE job_name = :j")
                .param("j", job)
                .query(String.class)
                .single();
    }

    private Consumer<String> subscribe(String topic) throws Exception {
        return client.newConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName(unique("test"))
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscribe();
    }

    @Test
    void aPulsarJobSendsThePayloadWithTheJobNameAsKey() throws Exception {
        String topic = "persistent://public/default/" + unique("invoices");
        String job = unique("send-invoices");
        try (Consumer<String> consumer = subscribe(topic)) {
            api.createJob(
                    job,
                    "0 0 0 1 1 *",
                    "\"type\":\"PULSAR\",\"target\":\"" + topic + "\",\"payload\":\"{\\\"n\\\":3}\"");
            api.trigger(job);

            dispatcher.drain();

            assertThat(runOutput(job)).startsWith("SUCCEEDED: Stored by Pulsar: " + topic + ", message id ");
            Message<String> message = consumer.receive(30, TimeUnit.SECONDS);
            assertThat(message).isNotNull();
            assertThat(message.getValue()).isEqualTo("{\"n\":3}");
            assertThat(message.getKey()).isEqualTo(job);
            assertThat(message.getProperty("x-schedkeel-job")).isEqualTo(job);
            assertThat(message.getProperty("x-schedkeel-run")).isNotBlank();
        }
    }

    @Test
    void aShortTopicNameIsInThePublicDefaultNamespace() throws Exception {
        String name = unique("ticks");
        String job = unique("tick");
        try (Consumer<String> consumer = subscribe("persistent://public/default/" + name)) {
            api.createJob(job, "0 0 0 1 1 *", "\"type\":\"PULSAR\",\"target\":\"" + name + "\",\"payload\":\"tick\"");
            api.trigger(job);

            dispatcher.drain();

            assertThat(runOutput(job)).startsWith("SUCCEEDED: Stored by Pulsar: persistent://public/default/" + name);
            assertThat(consumer.receive(30, TimeUnit.SECONDS).getValue()).isEqualTo("tick");
        }
    }

    @Test
    void aTenantThatDoesNotExistFailsTheRun() throws Exception {
        String job = unique("no-tenant");
        api.createJob(
                job,
                "0 0 0 1 1 *",
                "\"type\":\"PULSAR\",\"target\":\"persistent://no-such-tenant/ns/t\",\"payload\":\"p\","
                        + "\"timeoutSeconds\":20");
        api.trigger(job);

        dispatcher.drain();

        assertThat(runOutput(job)).startsWith("FAILED: Pulsar did not ");
    }
}
