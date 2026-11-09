package io.github.jkanie.schedkeel.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OneShotShellRunnerTests {

    @Test
    void quotesAnArgumentThatHoldsSpaces() {
        assertThat(OneShotShellRunner.quoted(new String[] {"create", "--cron", "0 0 2 * * *"}))
                .containsExactly("create", "--cron", "\"0 0 2 * * *\"");
    }

    @Test
    void leavesSingleWordsAndQuotedArgumentsAlone() {
        assertThat(OneShotShellRunner.quoted(new String[] {"jobs", "\"a b\""})).containsExactly("jobs", "\"a b\"");
    }
}
