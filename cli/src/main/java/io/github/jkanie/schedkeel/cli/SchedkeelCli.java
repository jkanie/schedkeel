package io.github.jkanie.schedkeel.cli;

import java.io.PrintWriter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/** A terminal client for the schedkeel API. */
@SpringBootApplication
@EnableConfigurationProperties(CliProperties.class)
public class SchedkeelCli {

    public static void main(String[] args) {
        SpringApplication.run(SchedkeelCli.class, args);
    }

    @Bean
    ShellRunner shellRunner(CommandParser parser, CommandRegistry registry) {
        return new OneShotShellRunner(parser, registry, new PrintWriter(System.out, true));
    }
}
