/**
 * Run history and the run queue.
 *
 * <p>Runs live in Spring Data JDBC. State changes use plain SQL with
 * {@code FOR UPDATE SKIP LOCKED}, so many server instances can claim work
 * from the same queue without taking the same run twice.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Runs")
package io.github.jkanie.schedkeel.run;
