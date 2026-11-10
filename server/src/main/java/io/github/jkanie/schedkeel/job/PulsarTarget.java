package io.github.jkanie.schedkeel.job;

import java.util.regex.Pattern;

/**
 * Where a PULSAR job sends: a topic name. A short name ({@code orders}) or a
 * {@code tenant/namespace/topic} name becomes a persistent topic, as the Pulsar
 * client reads it. A full name keeps its own domain:
 * {@code persistent://tenant/namespace/topic} or {@code non-persistent://...}.
 */
public record PulsarTarget(String topic) {

    // Pulsar allows letters, digits and - _ = : . in tenant, namespace and topic names.
    private static final String PART = "[A-Za-z0-9_=:.\\-]+";
    private static final Pattern SHORT = Pattern.compile(PART);
    private static final Pattern LOCAL = Pattern.compile(PART + "/" + PART + "/" + PART);
    private static final Pattern FULL =
            Pattern.compile("(persistent|non-persistent)://" + PART + "/" + PART + "/" + PART);

    public static PulsarTarget parse(String target) {
        String t = target.trim();
        if (FULL.matcher(t).matches()) {
            return new PulsarTarget(t);
        }
        if (LOCAL.matcher(t).matches()) {
            return new PulsarTarget("persistent://" + t);
        }
        if (SHORT.matcher(t).matches()) {
            return new PulsarTarget("persistent://public/default/" + t);
        }
        throw new IllegalArgumentException("'" + target + "' is not a Pulsar topic. Use a name such as orders, "
                + "tenant/namespace/topic or persistent://tenant/namespace/topic.");
    }
}
