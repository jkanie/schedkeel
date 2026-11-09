package io.github.jkanie.schedkeel.execution;

import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobType;
import io.github.jkanie.schedkeel.run.Run;

/** Executes 1 job type. Throwing marks the run as failed. */
interface JobRunner {

    JobType type();

    /** Runs the job and returns text to keep as the run's output. */
    String run(JobDefinition job, Run run) throws Exception;
}
