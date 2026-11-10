package io.github.jkanie.schedkeel;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.pulsar.PulsarContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL, Redis, Kafka, RabbitMQ, Pulsar in containers, plus a clock that tests move by hand. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer("redis:8-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.1");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4.1-alpine");
    }

    @Bean
    @ServiceConnection
    PulsarContainer pulsar() {
        // The broker release matches the Pulsar client that Spring Boot 4.1.2 manages (4.2.5).
        return new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.2.5"));
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
