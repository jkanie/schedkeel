package io.github.jkanie.schedkeel.execution;

/** A job ran but did not succeed. The message becomes the run's output. */
class JobFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    JobFailedException(String message) {
        super(message);
    }
}
