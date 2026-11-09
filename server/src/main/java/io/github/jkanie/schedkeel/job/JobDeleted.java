package io.github.jkanie.schedkeel.job;

/** Published in the same transaction after a job is deleted. */
public record JobDeleted(long jobId, String name) {}
