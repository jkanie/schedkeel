package io.github.jkanie.schedkeel.run;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import io.github.jkanie.schedkeel.job.JobCatalog;
import io.github.jkanie.schedkeel.job.JobDefinition;
import io.github.jkanie.schedkeel.job.JobNotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RunController {

    private final RunService runs;
    private final JobCatalog jobs;
    private final PagedResourcesAssembler<Run> pages;
    private final Clock clock;

    RunController(RunService runs, JobCatalog jobs, PagedResourcesAssembler<Run> pages, Clock clock) {
        this.runs = runs;
        this.jobs = jobs;
        this.pages = pages;
        this.clock = clock;
    }

    @GetMapping("/api/runs")
    PagedModel<EntityModel<RunResource>> list(
            @RequestParam(required = false) String job,
            @RequestParam(required = false) RunStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return pages.toModel(runs.page(job, status, pageable), RunController::toModel);
    }

    @GetMapping("/api/runs/{id}")
    EntityModel<RunResource> get(@PathVariable long id) {
        return toModel(runs.get(id));
    }

    /** Queues a run of the job now, outside its schedule. */
    @PostMapping("/api/jobs/{name}/runs")
    ResponseEntity<EntityModel<RunResource>> trigger(@PathVariable String name) {
        JobDefinition job = jobs.findByName(name).orElseThrow(() -> new JobNotFoundException(name));
        EntityModel<RunResource> model = toModel(runs.enqueue(job, TriggerSource.MANUAL, Instant.now(clock)));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    static EntityModel<RunResource> toModel(Run run) {
        return EntityModel.of(
                RunResource.of(run),
                linkTo(methodOn(RunController.class).get(run.getId())).withSelfRel(),
                linkTo(methodOn(RunController.class).list(run.getJobName(), null, Pageable.unpaged()))
                        .withRel("job-runs"));
    }
}
