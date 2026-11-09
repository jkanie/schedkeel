package io.github.jkanie.schedkeel.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Thrown when a request fails a check that bean validation cannot express. Maps to 400. */
class InvalidJobException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    InvalidJobException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
