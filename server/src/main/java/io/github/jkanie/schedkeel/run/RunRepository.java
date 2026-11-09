package io.github.jkanie.schedkeel.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

interface RunRepository extends ListCrudRepository<Run, Long>, ListPagingAndSortingRepository<Run, Long> {

    Page<Run> findByJobName(String jobName, Pageable pageable);

    Page<Run> findByStatus(RunStatus status, Pageable pageable);

    Page<Run> findByJobNameAndStatus(String jobName, RunStatus status, Pageable pageable);

    long countByStatus(RunStatus status);
}
