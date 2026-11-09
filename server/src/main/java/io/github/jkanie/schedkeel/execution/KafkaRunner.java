package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/** Publishes the payload to the job's topic, keyed by the job name. */
@Component
class KafkaRunner implements JobRunner {

    private final KafkaTemplate<String, String> kafka;

    KafkaRunner(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public JobType type() {
        return JobType.KAFKA;
    }

    @Override
    public String run(JobDefinition job, Run run) throws Exception {
        String payload = job.payload() == null ? "" : job.payload();
        SendResult<String, String> sent =
                kafka.send(job.target(), job.name(), payload).get(job.timeoutSeconds(), TimeUnit.SECONDS);
        var meta = sent.getRecordMetadata();
        return "Sent to " + meta.topic() + " partition " + meta.partition() + " offset " + meta.offset();
    }
}
