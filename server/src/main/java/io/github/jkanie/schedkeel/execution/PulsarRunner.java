package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.job.PulsarTarget;
import io.github.jkanie.schedkeel.run.Run;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pulsar.client.api.MessageId;
import org.springframework.pulsar.PulsarException;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends the payload to the job's Pulsar topic and waits until the broker has
 * stored it. The message key is the job name, and 2 properties name the job
 * and the run, as on the RabbitMQ jobs.
 */
@Component
class PulsarRunner implements JobRunner {

    private final PulsarTemplate<String> pulsar;

    PulsarRunner(PulsarTemplate<String> pulsar) {
        this.pulsar = pulsar;
    }

    @Override
    public JobType type() {
        return JobType.PULSAR;
    }

    @Override
    public String run(JobDefinition job, Run run) throws Exception {
        String topic = PulsarTarget.parse(job.target()).topic();
        String payload = job.payload() == null ? "" : job.payload();
        MessageId id;
        try {
            id = pulsar.newMessage(payload)
                    .withTopic(topic)
                    .withMessageCustomizer(message -> message.key(job.name())
                            .property("x-schedkeel-job", job.name())
                            .property("x-schedkeel-run", String.valueOf(run.getId())))
                    .sendAsync()
                    .get(job.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new JobFailedException("Pulsar did not store the message on " + topic + ": " + cause.getMessage());
        } catch (PulsarException e) {
            // Thrown before the send starts, for example when the producer cannot be created.
            throw new JobFailedException("Pulsar did not store the message on " + topic + ": " + e.getMessage());
        } catch (TimeoutException e) {
            throw new JobFailedException(
                    "Pulsar did not confirm the message on " + topic + " within " + job.timeoutSeconds() + " s.");
        }
        return "Stored by Pulsar: " + topic + ", message id " + id + ".";
    }
}
