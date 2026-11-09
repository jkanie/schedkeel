package io.github.jkanie.schedkeel.job;

/**
 * Where an AMQP job publishes: {@code exchange/routing-key}. The part before
 * the 1st slash is the exchange. An empty exchange is RabbitMQ's default
 * exchange, which routes to the queue named by the routing key. With no slash,
 * the whole target is the exchange and the routing key is empty.
 */
public record AmqpTarget(String exchange, String routingKey) {

    public static AmqpTarget parse(String target) {
        String trimmed = target.trim();
        int slash = trimmed.indexOf('/');
        AmqpTarget parsed = slash < 0
                ? new AmqpTarget(trimmed, "")
                : new AmqpTarget(trimmed.substring(0, slash), trimmed.substring(slash + 1));
        if (parsed.exchange().isEmpty() && parsed.routingKey().isEmpty()) {
            throw new IllegalArgumentException(
                    "An AMQP target needs an exchange, a routing key or both, as exchange/routing-key.");
        }
        return parsed;
    }

    /** For run output and errors, for example "exchange 'billing', routing key 'invoice.due'". */
    public String describe() {
        String ex = exchange.isEmpty() ? "the default exchange" : "exchange '" + exchange + "'";
        return ex + ", routing key '" + routingKey + "'";
    }
}
