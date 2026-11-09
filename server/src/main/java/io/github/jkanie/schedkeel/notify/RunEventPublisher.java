package io.github.jkanie.schedkeel.notify;

import io.github.jkanie.schedkeel.run.RunFinished;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Sends every finished run to the run-events topic as JSON, keyed by job name. */
@Component
class RunEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final String topic;

    RunEventPublisher(
            KafkaTemplate<String, String> kafka,
            JsonMapper json,
            @Value("${schedkeel.kafka.run-events-topic}") String topic) {
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
    }

    @ApplicationModuleListener
    void on(RunFinished event) throws Exception {
        kafka.send(topic, event.jobName(), json.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
    }
}
