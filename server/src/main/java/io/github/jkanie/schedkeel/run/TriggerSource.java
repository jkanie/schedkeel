package io.github.jkanie.schedkeel.run;

/** What created a run. */
public enum TriggerSource {
    SCHEDULE,
    MANUAL,
    KAFKA,
    RETRY
}
