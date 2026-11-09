package io.github.jkanie.schedkeel.run;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import io.github.jkanie.schedkeel.job.JobResource;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.stereotype.Component;

/** Adds run links to every job the API returns, so the job module needs no link to this one. */
@Component
class JobRunLinks implements RepresentationModelProcessor<EntityModel<JobResource>> {

    @Override
    public EntityModel<JobResource> process(EntityModel<JobResource> model) {
        JobResource job = model.getContent();
        if (job != null) {
            model.add(linkTo(methodOn(RunController.class).list(job.name(), null, Pageable.unpaged()))
                    .withRel("runs"));
            model.add(linkTo(methodOn(RunController.class).trigger(job.name())).withRel("trigger"));
        }
        return model;
    }
}
