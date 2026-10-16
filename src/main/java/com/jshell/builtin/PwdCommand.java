package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.*;
import java.nio.file.Path;

/**
 * Built-in {@code pwd} command.
 *
 * <p>Usage: {@code pwd [-L | -P]}
 * <ul>
 *   <li>{@code -L} (default) — print the logical current directory (from {@code $PWD})</li>
 *   <li>{@code -P} — print the physical path (resolving all symlinks)</li>
 * </ul>
 */
public class PwdCommand implements Builtin {

    @Override
    public String name() { return "pwd"; }

    @Override
    public String usage() { return "pwd [-L | -P]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        boolean physical = false;
        for (int i = 1; i < args.length; i++) {
            if ("-P".equals(args[i])) physical = true;
            else if ("-L".equals(args[i])) physical = false;
        }

        Path cwd = env.getWorkingDirectory();

        if (physical) {
            try {
                cwd = cwd.toRealPath();
            } catch (IOException e) {
                printErr(stderr, "pwd: " + e.getMessage());
                return 1;
            }
        }

        try {
            stdout.write((cwd.toString() + "\n").getBytes());
            stdout.flush();
        } catch (IOException e) {
            return 1;
        }
        return 0;
    }

    private void printErr(OutputStream err, String msg) {
        try { err.write((msg + "\n").getBytes()); err.flush(); } catch (IOException ignored) {}
    }
}
