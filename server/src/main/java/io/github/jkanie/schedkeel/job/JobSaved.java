package io.github.jkanie.schedkeel.job;

/** Published in the same transaction after a job is created or changed. */
public record JobSaved(JobDefinition job) {}
