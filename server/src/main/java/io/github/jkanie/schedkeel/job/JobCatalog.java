package io.github.jkanie.schedkeel.job;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to job definitions for the other modules. */
@Service
public class JobCatalog {

    private final JobDefinitionRepository jobs;

    JobCatalog(JobDefinitionRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Optional<JobDefinition> findById(long id) {
        return jobs.findById(id).map(Job::toDefinition);
    }

    @Transactional(readOnly = true)
    public Optional<JobDefinition> findByName(String name) {
        return jobs.findByName(name).map(Job::toDefinition);
    }
}
