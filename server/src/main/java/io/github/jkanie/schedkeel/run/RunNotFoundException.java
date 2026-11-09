package io.github.jkanie.schedkeel.run;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class RunNotFoundException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    RunNotFoundException(long id) {
        super(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No run has the id " + id + "."),
                null);
    }
}
