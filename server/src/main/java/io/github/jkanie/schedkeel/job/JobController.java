package io.github.jkanie.schedkeel.job;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
class JobController {

    private final JobService service;
    private final JobAssembler assembler = new JobAssembler();

    JobController(JobService service) {
        this.service = service;
    }

    @GetMapping
    CollectionModel<EntityModel<JobResource>> list() {
        return assembler
                .toCollectionModel(service.list())
                .add(linkTo(methodOn(JobController.class).list()).withSelfRel());
    }

    @GetMapping("/{name}")
    EntityModel<JobResource> get(@PathVariable String name) {
        return assembler.toModel(service.get(name));
    }

    @PostMapping
    ResponseEntity<EntityModel<JobResource>> create(@Valid @RequestBody JobRequest request) {
        EntityModel<JobResource> model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping("/{name}")
    EntityModel<JobResource> update(@PathVariable String name, @Valid @RequestBody JobRequest request) {
        return assembler.toModel(service.update(name, request));
    }

    @DeleteMapping("/{name}")
    ResponseEntity<Void> delete(@PathVariable String name) {
        service.delete(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{name}/pause")
    EntityModel<JobResource> pause(@PathVariable String name) {
        return assembler.toModel(service.setEnabled(name, false));
    }

    @PostMapping("/{name}/resume")
    EntityModel<JobResource> resume(@PathVariable String name) {
        return assembler.toModel(service.setEnabled(name, true));
    }

    @GetMapping("/{name}/history")
    CollectionModel<JobRevision> history(@PathVariable String name) {
        List<JobRevision> revisions = service.history(name);
        return CollectionModel.of(
                revisions,
                linkTo(methodOn(JobController.class).history(name)).withSelfRel(),
                linkTo(methodOn(JobController.class).get(name)).withRel("job"));
    }

    static final class JobAssembler implements RepresentationModelAssembler<Job, EntityModel<JobResource>> {

        @Override
        public EntityModel<JobResource> toModel(Job job) {
            String name = job.getName();
            EntityModel<JobResource> model = EntityModel.of(
                    job.toResource(),
                    linkTo(methodOn(JobController.class).get(name)).withSelfRel(),
                    linkTo(methodOn(JobController.class).history(name)).withRel("history"),
                    linkTo(methodOn(JobController.class).list()).withRel("jobs"));
            if (job.isEnabled()) {
                model.add(linkTo(methodOn(JobController.class).pause(name)).withRel("pause"));
            } else {
                model.add(linkTo(methodOn(JobController.class).resume(name)).withRel("resume"));
            }
            return model;
        }
    }
}
