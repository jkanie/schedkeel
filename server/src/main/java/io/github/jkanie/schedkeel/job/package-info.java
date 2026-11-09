/**
 * Job definitions: what runs, when it runs, and who changed it.
 *
 * <p>Jobs live in JPA with Hibernate Envers auditing, so every change keeps a
 * revision with the user who made it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Jobs")
package io.github.jkanie.schedkeel.job;
