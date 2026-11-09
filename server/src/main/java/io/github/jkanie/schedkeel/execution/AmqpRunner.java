package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.AmqpTarget;
import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the payload to a RabbitMQ exchange and waits for the broker's
 * confirm. A message that no queue receives comes back as returned, and the
 * run fails, because a silent drop would look like success.
 */
@Component
class AmqpRunner implements JobRunner {

    private final RabbitTemplate rabbit;

    AmqpRunner(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public JobType type() {
        return JobType.AMQP;
    }

    @Override
    public String run(JobDefinition job, Run run) throws Exception {
        AmqpTarget target = AmqpTarget.parse(job.target());
        String payload = job.payload() == null ? "" : job.payload();
        CorrelationData correlation = new CorrelationData("run-" + run.getId());
        rabbit.convertAndSend(
                target.exchange(),
                target.routingKey(),
                payload,
                message -> {
                    var props = message.getMessageProperties();
                    props.setMessageId("run-" + run.getId());
                    props.setAppId("schedkeel");
                    props.setHeader("x-schedkeel-job", job.name());
                    props.setHeader("x-schedkeel-run", run.getId());
                    return message;
                },
                correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(job.timeoutSeconds(), TimeUnit.SECONDS);
        if (!confirm.ack()) {
            throw new JobFailedException("RabbitMQ refused the message: " + confirm.reason());
        }
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new JobFailedException("RabbitMQ returned the message, because no queue is bound to "
                    + target.describe() + " (" + returned.getReplyCode() + " " + returned.getReplyText() + ").");
        }
        return "Confirmed by RabbitMQ: " + target.describe() + ".";
    }
}
