package io.github.jkanie.schedkeel.web;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** The API entry point. Clients start here and follow links. */
@RestController
class ApiRootController {

    @GetMapping("/api")
    RepresentationModel<?> root() {
        String base = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api")
                .toUriString();
        return new RepresentationModel<>()
                .add(Link.of(base).withSelfRel())
                .add(Link.of(base + "/jobs").withRel("jobs"))
                .add(Link.of(base + "/runs{?job,status,page,size,sort}").withRel("runs"))
                .add(Link.of(base + "/me").withRel("me"));
    }
}
