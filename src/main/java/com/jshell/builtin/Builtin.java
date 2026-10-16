package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface implemented by all shell built-in commands.
 *
 * <p>Built-ins are executed directly in the shell process (not via {@link ProcessBuilder})
 * and therefore have direct access to the {@link Environment} and can change the
 * shell's state (working directory, variables, etc.).
 */
public interface Builtin {

    /**
     * Returns the primary name of this built-in (e.g., {@code "cd"}, {@code "echo"}).
     */
    String name();

    /**
     * Returns any additional names this built-in should be registered under.
     * Default implementation returns an empty array.
     */
    default String[] aliases() {
        return new String[0];
    }

    /**
     * Executes the built-in command.
     *
     * @param args    The full argument vector, including {@code args[0]} which is the
     *                command name as invoked.
     * @param env     The current shell environment.
     * @param stdin   Standard input stream.
     * @param stdout  Standard output stream.
     * @param stderr  Standard error stream.
     * @return Exit status code (0 = success, non-zero = failure).
     */
    int execute(String[] args,
                Environment env,
                InputStream stdin,
                OutputStream stdout,
                OutputStream stderr);

    /**
     * Returns a one-line usage string for the built-in.
     */
    default String usage() {
        return name() + ": no help available";
    }
}
