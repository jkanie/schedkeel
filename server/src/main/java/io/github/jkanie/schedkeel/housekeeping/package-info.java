/**
 * Spring Batch jobs that keep the database small. Schedule them like any
 * other job, with type {@code BATCH} and the batch job name as the target.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Housekeeping")
package io.github.jkanie.schedkeel.housekeeping;
