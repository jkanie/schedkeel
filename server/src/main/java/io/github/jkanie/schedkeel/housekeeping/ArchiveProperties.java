package io.github.jkanie.schedkeel.housekeeping;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code schedkeel.archive}.
 *
 * @param directory where archive CSV files go
 * @param retentionDays how many days finished runs stay in the database
 * @param chunkSize how many runs 1 chunk reads, writes and commits
 */
@ConfigurationProperties("schedkeel.archive")
record ArchiveProperties(
        @DefaultValue("archive") Path directory,
        @DefaultValue("30") int retentionDays,
        @DefaultValue("500") int chunkSize) {}
