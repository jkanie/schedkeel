package io.github.jkanie.schedkeel.job;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.history.RevisionRepository;

interface JobDefinitionRepository extends JpaRepository<Job, Long>, RevisionRepository<Job, Long, Long> {

    Optional<Job> findByName(String name);

    boolean existsByName(String name);

    List<Job> findAllByOrderByNameAsc();
}
