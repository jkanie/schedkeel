package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LogRunner implements JobRunner {

    private static final Logger log = LoggerFactory.getLogger(LogRunner.class);

    @Override
    public JobType type() {
        return JobType.LOG;
    }

    @Override
    public String run(JobDefinition job, Run run) {
        String message = job.payload() == null ? job.target() : job.payload();
        log.info("[{} #{}] {}", job.name(), run.getId(), message);
        return message;
    }
}
