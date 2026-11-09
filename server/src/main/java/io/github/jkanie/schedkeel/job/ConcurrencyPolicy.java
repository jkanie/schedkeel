package io.github.jkanie.schedkeel.job;

/** What happens when a job is due while an earlier run is still active. */
public enum ConcurrencyPolicy {
    /** Start the new run anyway. */
    ALLOW,
    /** Record the new run as skipped. */
    FORBID
}
