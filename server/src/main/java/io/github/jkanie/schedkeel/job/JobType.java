package io.github.jkanie.schedkeel.job;

/** What a job does when it runs. */
public enum JobType {
    /** Writes the payload to the log. Useful for smoke tests and demos. */
    LOG,
    /** Calls the target URL. Sends the payload as a POST body when present. */
    HTTP,
    /** Publishes the payload to the target Kafka topic. */
    KAFKA,
    /** Starts the Spring Batch job named by the target. */
    BATCH
}
