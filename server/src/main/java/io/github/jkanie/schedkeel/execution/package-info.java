/**
 * Claims queued runs and executes them on virtual threads, with a timeout,
 * retries and Micrometer metrics.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Execution")
package io.github.jkanie.schedkeel.execution;
