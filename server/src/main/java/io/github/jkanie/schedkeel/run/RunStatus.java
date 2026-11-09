package io.github.jkanie.schedkeel.run;

/** Where a run is in its life. */
public enum RunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    SKIPPED;

    /** True once the run can no longer change. */
    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}
