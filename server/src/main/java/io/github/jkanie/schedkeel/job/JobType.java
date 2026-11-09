package io.github.jkanie.schedkeel.job;

/** What a job does when it runs. */
public enum JobType {
    /** Writes the payload to the log. Useful for smoke tests and demos. */
    LOG,
    /** Calls the target URL. Sends the payload as a POST body when present. */
    HTTP,
    /** Publishes the payload to the target Kafka topic. */
    KAFKA,
    /**
     * Publishes the payload to a RabbitMQ exchange. The target is
     * {@code exchange/routing-key}. An empty exchange ({@code /orders}) means
     * the default exchange, which routes to the queue named by the key.
     */
    AMQP,
    /** Starts the Spring Batch job named by the target. */
    BATCH
}
