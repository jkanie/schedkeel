package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Component;

/**
 * Starts the Spring Batch job named by the target. The run id is an
 * identifying parameter, so every run is a new job instance.
 */
@Component
class BatchRunner implements JobRunner {

    private final JobOperator operator;
    private final Map<String, Job> batchJobs;

    BatchRunner(JobOperator operator, List<Job> batchJobs) {
        this.operator = operator;
        this.batchJobs = batchJobs.stream().collect(Collectors.toMap(Job::getName, Function.identity()));
    }

    @Override
    public JobType type() {
        return JobType.BATCH;
    }

    @Override
    public String run(JobDefinition job, Run run) throws Exception {
        Job batchJob = batchJobs.get(job.target());
        if (batchJob == null) {
            throw new JobFailedException("No Spring Batch job is named '" + job.target() + "'. Known: "
                    + batchJobs.keySet().stream().sorted().toList());
        }
        var params = new JobParametersBuilder()
                .addLong("schedkeel.run", run.getId())
                .addLong("schedkeel.attempt", (long) run.getAttempt())
                .toJobParameters();
        JobExecution execution = operator.start(batchJob, params);
        String summary = "Batch " + batchJob.getName() + " execution " + execution.getId() + ": "
                + execution.getStatus() + " (" + execution.getExitStatus().getExitCode() + ")";
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw new JobFailedException(summary);
        }
        return summary;
    }
}
