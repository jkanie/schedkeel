package io.github.jkanie.schedkeel.cli;

import java.io.PrintWriter;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/**
 * Runs the command given on the command line, then exits, so the CLI fits in
 * scripts and cron. With no command it prints help. An argument that starts
 * with @ names a script file of commands, 1 per line.
 *
 * <p>Spring Shell starts its own prompt that reads standard input unless
 * spring.shell.interactive.enabled is false. This runner makes 1-shot use
 * the default, with no flag to remember.
 */
class OneShotShellRunner implements ShellRunner {

    private final NonInteractiveShellRunner delegate;

    OneShotShellRunner(CommandParser parser, CommandRegistry registry, PrintWriter out) {
        this.delegate = new NonInteractiveShellRunner(parser, registry, out);
    }

    @Override
    public void run(String[] args) throws Exception {
        delegate.run(args.length == 0 ? new String[] {"help"} : quoted(args));
    }

    /**
     * Spring Shell joins the arguments with spaces and splits them again, so
     * a cron such as "0 0 2 * * *" would fall apart into 6 words. Wrapping
     * each argument that holds a space in double quotes keeps it whole.
     */
    static String[] quoted(String[] args) {
        String[] out = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            boolean needsQuotes = arg.chars().anyMatch(Character::isWhitespace) && !arg.contains("\"");
            out[i] = needsQuotes ? '"' + arg + '"' : arg;
        }
        return out;
    }
}
