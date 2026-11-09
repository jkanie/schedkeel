package io.github.jkanie.schedkeel.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellScreen;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.autoconfigure.ShellTestClientAutoConfiguration;

// "version" keeps the runner busy with a harmless command while the context starts.
@SpringBootTest(args = "version")
@ImportAutoConfiguration(ShellTestClientAutoConfiguration.class)
class CliApplicationTests {

    @Autowired
    ShellTestClient shell;

    @Autowired
    CommandParser parser;

    @Autowired
    CommandRegistry registry;

    @Test
    void registersEveryCommand() throws Exception {
        ShellScreen help = shell.sendCommand("help");
        assertThat(String.join("\n", help.lines()))
                .contains("jobs", "job", "create", "trigger", "pause", "resume", "delete", "runs", "history");
    }

    @Test
    void theOneShotRunnerPrintsAndFlushesTheCommandOutput() throws Exception {
        StringWriter out = new StringWriter();
        new OneShotShellRunner(parser, registry, new PrintWriter(out, true)).run(new String[0]);
        assertThat(out.toString()).as("no command prints help").contains("trigger", "Run a job now");
    }
}
