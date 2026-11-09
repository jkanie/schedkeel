/**
 * Tells the outside world about finished runs. Listens for
 * {@code RunFinished} through the Spring Modulith event publication
 * registry, so an event survives a crash between commit and send.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notifications")
package io.github.jkanie.schedkeel.notify;
