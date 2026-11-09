package io.github.jkanie.schedkeel.run;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** One execution of a job. */
@Table("runs")
public class Run {

    @Id
    private Long id;

    private long jobId;
    private String jobName;
    private RunStatus status;
    private TriggerSource triggerSource;
    private int attempt;
    private Instant scheduledFor;
    private Instant notBefore;
    private Instant startedAt;
    private Instant finishedAt;
    private String owner;
    private String output;

    Run() {}

    static Run create(
            long jobId,
            String jobName,
            RunStatus status,
            TriggerSource source,
            int attempt,
            Instant scheduledFor,
            Instant notBefore) {
        Run run = new Run();
        run.jobId = jobId;
        run.jobName = jobName;
        run.status = status;
        run.triggerSource = source;
        run.attempt = attempt;
        run.scheduledFor = scheduledFor;
        run.notBefore = notBefore;
        return run;
    }

    void skip(Instant now, String reason) {
        this.status = RunStatus.SKIPPED;
        this.finishedAt = now;
        this.output = reason;
    }

    public Long getId() {
        return id;
    }

    public long getJobId() {
        return jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public RunStatus getStatus() {
        return status;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public int getAttempt() {
        return attempt;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getOwner() {
        return owner;
    }

    public String getOutput() {
        return output;
    }
}
