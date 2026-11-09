/**
 * Starts runs from outside the schedule: a Kafka message whose value is a
 * job name queues a run of that job.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Triggers")
package io.github.jkanie.schedkeel.trigger;
