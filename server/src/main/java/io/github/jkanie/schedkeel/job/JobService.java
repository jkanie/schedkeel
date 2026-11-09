package io.github.jkanie.schedkeel.job;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.history.Revision;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JobService {

    private final JobDefinitionRepository jobs;
    private final ApplicationEventPublisher events;

    JobService(JobDefinitionRepository jobs, ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.events = events;
    }

    @Transactional(readOnly = true)
    List<Job> list() {
        return jobs.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    Job get(String name) {
        return jobs.findByName(name).orElseThrow(() -> new JobNotFoundException(name));
    }

    @Transactional
    Job create(JobRequest request) {
        check(request);
        if (jobs.existsByName(request.name())) {
            throw new InvalidJobException(HttpStatus.CONFLICT, "A job named '" + request.name() + "' already exists.");
        }
        Job job = new Job(request.name());
        job.apply(request);
        return saved(job);
    }

    @Transactional
    Job update(String name, JobRequest request) {
        check(request);
        if (!name.equals(request.name())) {
            throw new InvalidJobException(
                    HttpStatus.BAD_REQUEST,
                    "The name in the body must match the name in the path. Renaming is not supported.");
        }
        Job job = get(name);
        job.apply(request);
        return saved(job);
    }

    @Transactional
    Job setEnabled(String name, boolean enabled) {
        Job job = get(name);
        job.setEnabled(enabled);
        return saved(job);
    }

    @Transactional
    void delete(String name) {
        Job job = get(name);
        jobs.delete(job);
        events.publishEvent(new JobDeleted(job.getId(), job.getName()));
    }

    @Transactional(readOnly = true)
    List<JobRevision> history(String name) {
        Job job = get(name);
        return jobs.findRevisions(job.getId()).getContent().reversed().stream()
                .map(JobService::toRevision)
                .toList();
    }

    private Job saved(Job job) {
        Job saved = jobs.saveAndFlush(job);
        events.publishEvent(new JobSaved(saved.toDefinition()));
        return saved;
    }

    private static JobRevision toRevision(Revision<Long, Job> revision) {
        AuditRevision meta = revision.getMetadata().getDelegate();
        return new JobRevision(
                revision.getRequiredRevisionNumber(),
                revision.getMetadata().getRevisionType().name(),
                revision.getRequiredRevisionInstant(),
                meta.getUsername(),
                revision.getEntity().toResource());
    }

    static void check(JobRequest request) {
        if (!CronExpression.isValidExpression(request.cron().trim())) {
            throw new InvalidJobException(
                    HttpStatus.BAD_REQUEST,
                    "'" + request.cron() + "' is not a valid cron expression. Use 6 fields: "
                            + "second, minute, hour, day of month, month, day of week.");
        }
        try {
            ZoneId.of(request.timeZoneOrDefault());
        } catch (DateTimeException e) {
            throw new InvalidJobException(
                    HttpStatus.BAD_REQUEST, "'" + request.timeZone() + "' is not a known time zone.");
        }
        if (request.type() == JobType.HTTP && !request.target().trim().matches("https?://.+")) {
            throw new InvalidJobException(
                    HttpStatus.BAD_REQUEST, "An HTTP job needs a target that starts with http:// or https://.");
        }
        if (request.type() == JobType.AMQP) {
            try {
                AmqpTarget.parse(request.target());
            } catch (IllegalArgumentException e) {
                throw new InvalidJobException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
    }
}
