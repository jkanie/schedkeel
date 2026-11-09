package io.github.jkanie.schedkeel.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Thrown when no job has the requested name. Maps to 404. */
public class JobNotFoundException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    public JobNotFoundException(String name) {
        super(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No job is named '" + name + "'."),
                null);
    }
}
