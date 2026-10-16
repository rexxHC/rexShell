package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.*;

/**
 * Built-in {@code exit} command.
 *
 * <p>Usage: {@code exit [n]}
 *
 * <p>Exits the shell with status {@code n} (default: last exit status {@code $?}).
 * Uses {@link ShellExitException} so the REPL can catch it cleanly instead of
 * calling {@link System#exit} directly in a built-in.
 */
public class ExitCommand implements Builtin {

    @Override
    public String name() { return "exit"; }

    @Override
    public String usage() { return "exit [n]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        int status;
        if (args.length > 1) {
            try {
                status = Integer.parseInt(args[1].trim());
                // Shell convention: exit code is taken mod 256
                status = status & 0xFF;
            } catch (NumberFormatException e) {
                printErr(stderr, "exit: " + args[1] + ": numeric argument required");
                status = 2;
            }
        } else {
            status = env.getLastExitStatus();
        }

        throw new ShellExitException(status);
    }

    private void printErr(OutputStream stderr, String msg) {
        try {
            stderr.write((msg + "\n").getBytes());
            stderr.flush();
        } catch (IOException ignored) {}
    }
}
