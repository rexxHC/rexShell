package com.jshell.builtin;

import com.jshell.environment.Environment;
import com.jshell.platform.Platform;
import com.jshell.platform.PlatformFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Built-in {@code cd} command.
 *
 * <p>Usage:
 * <pre>
 *   cd          — change to $HOME
 *   cd -        — change to $OLDPWD (previous directory)
 *   cd [dir]    — change to [dir]
 * </pre>
 *
 * <p>Updates {@code $PWD} and {@code $OLDPWD} in the environment.
 */
public class CdCommand implements Builtin {

    private final Platform platform = PlatformFactory.detect();

    @Override
    public String name() { return "cd"; }

    @Override
    public String usage() { return "cd [-L|-P] [dir]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        // Parse optional -L / -P flags (logical / physical resolution)
        boolean physical = false;
        int argStart = 1;
        for (int i = 1; i < args.length; i++) {
            if ("-L".equals(args[i])) { physical = false; argStart = i + 1; }
            else if ("-P".equals(args[i])) { physical = true; argStart = i + 1; }
            else break;
        }

        Path target;

        if (argStart >= args.length) {
            // No argument: go to $HOME
            String home = env.get("HOME");
            if (home == null || home.isBlank()) {
                home = platform.getHomeDirectory().toString();
            }
            target = Paths.get(home);
        } else if ("-".equals(args[argStart])) {
            // cd - : go to $OLDPWD
            Path prev = env.getPreviousDirectory();
            if (prev == null) {
                printErr(stderr, "cd: OLDPWD not set");
                return 1;
            }
            target = prev;
            // Print the new directory (bash behaviour)
            printOut(stdout, target.toString());
        } else {
            target = Paths.get(args[argStart]);
        }

        // Resolve relative to current working directory
        if (!target.isAbsolute()) {
            target = env.getWorkingDirectory().resolve(target);
        }

        // Normalize the path
        target = target.normalize();

        // Optionally resolve symlinks (-P)
        if (physical) {
            try {
                target = target.toRealPath();
            } catch (IOException e) {
                printErr(stderr, "cd: " + args[argStart] + ": " + e.getMessage());
                return 1;
            }
        }

        // Validate
        if (!Files.exists(target)) {
            printErr(stderr, "cd: " + target + ": No such file or directory");
            return 1;
        }
        if (!Files.isDirectory(target)) {
            printErr(stderr, "cd: " + target + ": Not a directory");
            return 1;
        }

        env.setWorkingDirectory(target);
        return 0;
    }

    private void printOut(OutputStream out, String msg) {
        try { out.write((msg + "\n").getBytes()); out.flush(); } catch (IOException ignored) {}
    }

    private void printErr(OutputStream err, String msg) {
        try { err.write((msg + "\n").getBytes()); err.flush(); } catch (IOException ignored) {}
    }
}
