package io.github.jkanie.schedkeel.job;

import java.time.Instant;
import org.springframework.hateoas.server.core.Relation;

/** One audited version of a job. */
@Relation(collectionRelation = "revisions", itemRelation = "revision")
public record JobRevision(long revision, String change, Instant changedAt, String changedBy, JobResource job) {}
