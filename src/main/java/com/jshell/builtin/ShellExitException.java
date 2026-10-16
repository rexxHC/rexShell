package com.jshell.builtin;

/**
 * Signal exception thrown by {@link ExitCommand} to request the shell to terminate.
 *
 * <p>Using an exception keeps {@code exit} testable without calling {@link System#exit}
 * and allows the REPL to perform cleanup (history flush, job teardown) before exiting.
 */
public class ShellExitException extends RuntimeException {

    private final int status;

    public ShellExitException(int status) {
        super("exit " + status);
        this.status = status;
    }

    /** Returns the requested exit status code. */
    public int getStatus() { return status; }
}
