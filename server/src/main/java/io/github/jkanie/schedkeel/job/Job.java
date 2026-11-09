package io.github.jkanie.schedkeel.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Audited
@Table(name = "jobs")
@EntityListeners(AuditingEntityListener.class)
class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 120)
    private String cron;

    @Column(nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobType type;

    @Column(nullable = false, length = 2000)
    private String target;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConcurrencyPolicy concurrency;

    @Column(nullable = false)
    private int maxRetries;

    @Column(nullable = false)
    private int retryBackoffSeconds;

    @Column(nullable = false)
    private int timeoutSeconds;

    @Version
    @NotAudited
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @NotAudited
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @NotAudited
    @Column(updatable = false, length = 80)
    private String createdBy;

    @LastModifiedDate
    @NotAudited
    @Column(nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @NotAudited
    @Column(length = 80)
    private String updatedBy;

    protected Job() {}

    Job(String name) {
        this.name = name;
    }

    void apply(JobRequest request) {
        this.description = request.description();
        this.cron = request.cron().trim();
        this.timeZone = request.timeZoneOrDefault();
        this.type = request.type();
        this.target = request.target().trim();
        this.payload = request.payload();
        this.enabled = request.enabledOrDefault();
        this.concurrency = request.concurrencyOrDefault();
        this.maxRetries = request.maxRetriesOrDefault();
        this.retryBackoffSeconds = request.retryBackoffSecondsOrDefault();
        this.timeoutSeconds = request.timeoutSecondsOrDefault();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    JobDefinition toDefinition() {
        return new JobDefinition(
                id,
                name,
                cron,
                timeZone,
                type,
                target,
                payload,
                enabled,
                concurrency,
                maxRetries,
                retryBackoffSeconds,
                timeoutSeconds);
    }

    JobResource toResource() {
        return new JobResource(
                name,
                description,
                cron,
                timeZone,
                type,
                target,
                payload,
                enabled,
                concurrency,
                maxRetries,
                retryBackoffSeconds,
                timeoutSeconds,
                createdAt,
                createdBy,
                updatedAt,
                updatedBy);
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    boolean isEnabled() {
        return enabled;
    }
}
