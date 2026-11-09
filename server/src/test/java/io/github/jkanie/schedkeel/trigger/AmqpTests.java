package io.github.jkanie.schedkeel.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.TestData;
import io.github.jkanie.schedkeel.execution.Dispatcher;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class AmqpTests {

    @Autowired
    Api api;

    @Autowired
    TestData data;

    @Autowired
    Dispatcher dispatcher;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    RabbitAdmin admin;

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

    @Test
    void anAmqpJobPublishesToAnExchangeAndWaitsForTheConfirm() throws Exception {
        String exchange = unique("billing");
        String queue = unique("invoices");
        admin.declareExchange(new TopicExchange(exchange, false, true));
        admin.declareQueue(new Queue(queue, false, false, true));
        admin.declareBinding(BindingBuilder.bind(new Queue(queue))
                .to(new TopicExchange(exchange))
                .with("invoice.*"));
        String job = unique("send-invoices");
        api.createJob(
                job,
                "0 0 0 1 1 *",
                "\"type\":\"AMQP\",\"target\":\"" + exchange + "/invoice.due\",\"payload\":\"{\\\"n\\\":3}\"");
        api.trigger(job);

        dispatcher.drain();

        assertThat(runOutput(job))
                .isEqualTo("SUCCEEDED: Confirmed by RabbitMQ: exchange '" + exchange + "', routing key 'invoice.due'.");
        Message message = rabbit.receive(queue, 10_000);
        assertThat(message).isNotNull();
        assertThat(new String(message.getBody())).isEqualTo("{\"n\":3}");
        assertThat(message.getMessageProperties().getAppId()).isEqualTo("schedkeel");
        assertThat(message.getMessageProperties().<String>getHeader("x-schedkeel-job"))
                .isEqualTo(job);
    }

    @Test
    void theDefaultExchangeRoutesToTheQueueNamedByTheKey() throws Exception {
        String queue = unique("direct");
        admin.declareQueue(new Queue(queue, false, false, true));
        String job = unique("to-queue");
        api.createJob(job, "0 0 0 1 1 *", "\"type\":\"AMQP\",\"target\":\"/" + queue + "\",\"payload\":\"hi\"");
        api.trigger(job);

        dispatcher.drain();

        assertThat(runOutput(job)).startsWith("SUCCEEDED: Confirmed by RabbitMQ: the default exchange");
        assertThat(new String(rabbit.receive(queue, 10_000).getBody())).isEqualTo("hi");
    }

    @Test
    void aMessageThatReachesNoQueueFailsTheRun() throws Exception {
        String exchange = unique("nobody-listens");
        admin.declareExchange(new TopicExchange(exchange, false, true));
        String job = unique("lost");
        api.createJob(job, "0 0 0 1 1 *", "\"type\":\"AMQP\",\"target\":\"" + exchange + "/x\",\"payload\":\"p\"");
        api.trigger(job);

        dispatcher.drain();

        assertThat(runOutput(job))
                .startsWith("FAILED: RabbitMQ returned the message, because no queue is bound to exchange '" + exchange)
                .endsWith("(312 NO_ROUTE).");
    }

    @Test
    void anExchangeThatDoesNotExistFailsTheRun() throws Exception {
        String job = unique("no-exchange");
        api.createJob(job, "0 0 0 1 1 *", "\"type\":\"AMQP\",\"target\":\"no-such-exchange/x\",\"payload\":\"p\"");
        api.trigger(job);

        dispatcher.drain();

        assertThat(runOutput(job)).startsWith("FAILED: RabbitMQ refused the message:");
    }

    @Test
    void aMessageOnTheTriggerQueueQueuesARun() throws Exception {
        String job = unique("on-demand");
        api.createJob(job, "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"x\"");

        rabbit.convertAndSend("", "schedkeel.triggers", job);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbc.sql("SELECT trigger_source FROM runs WHERE job_name = :j")
                                .param("j", job)
                                .query(String.class)
                                .list())
                        .containsExactly("AMQP"));
    }
}
