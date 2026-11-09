package io.github.jkanie.schedkeel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/** Reads a topic from the start, for assertions. */
public final class KafkaTestConsumer {

    private KafkaTestConsumer() {}

    /** Polls until a record with this key arrives or the wait runs out. */
    public static List<ConsumerRecord<String, String>> recordsWithKey(
            KafkaConnectionDetails kafka, String topic, String key, Duration wait) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        var factory = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (Consumer<String, String> consumer = factory.createConsumer()) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + wait.toNanos();
            while (found.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }
}
