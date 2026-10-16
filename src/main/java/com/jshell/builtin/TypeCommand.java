package com.jshell.builtin;

import com.jshell.environment.Environment;
import com.jshell.platform.Platform;
import com.jshell.platform.PlatformFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Built-in {@code type} command.
 *
 * <p>Usage: {@code type [-a] [-t] [-p] name [name ...]}
 * <ul>
 *   <li>(default) — describe what each name is</li>
 *   <li>{@code -t} — print a single word: {@code alias}, {@code builtin}, {@code function},
 *       {@code file}, or {@code keyword}</li>
 *   <li>{@code -p} — print the path to the executable (files only)</li>
 *   <li>{@code -a} — print all locations where name is found</li>
 * </ul>
 */
public class TypeCommand implements Builtin {

    private final BuiltinRegistry registry;
    private final Platform        platform = PlatformFactory.detect();

    public TypeCommand(BuiltinRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() { return "type"; }

    @Override
    public String usage() { return "type [-a] [-t] [-p] name [name ...]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        boolean typeOnly = false;
        boolean pathOnly = false;
        boolean all      = false;
        int argStart     = 1;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-t" -> { typeOnly = true; argStart = i + 1; }
                case "-p" -> { pathOnly = true; argStart = i + 1; }
                case "-a" -> { all      = true; argStart = i + 1; }
                default   -> { break; }
            }
        }

        if (argStart >= args.length) {
            printErr(stderr, "type: missing argument");
            return 1;
        }

        int status = 0;
        for (int i = argStart; i < args.length; i++) {
            String name = args[i];
            int result  = describeCommand(name, env, stdout, stderr, typeOnly, pathOnly, all);
            if (result != 0) status = result;
        }
        return status;
    }

    private int describeCommand(String name,
                                Environment env,
                                OutputStream stdout,
                                OutputStream stderr,
                                boolean typeOnly,
                                boolean pathOnly,
                                boolean all) {
        boolean found = false;

        // 1. Check built-ins
        if (registry.isBuiltin(name)) {
            found = true;
            if (!pathOnly) {
                if (typeOnly) println(stdout, "builtin");
                else println(stdout, name + " is a shell builtin");
                if (!all) return 0;
            }
        }

        // 2. Check aliases (Phase 5 — for now report "not alias")
        // (AliasManager will be added in Phase 5)

        // 3. Check PATH
        String pathEnv = env.get("PATH");
        Optional<Path> resolved = platform.resolveExecutable(name, pathEnv);
        if (resolved.isPresent()) {
            found = true;
            String filePath = resolved.get().toString();
            if (typeOnly) println(stdout, "file");
            else if (pathOnly) println(stdout, filePath);
            else println(stdout, name + " is " + filePath);
            if (!all) return 0;
        }

        if (!found) {
            printErr(stderr, "type: " + name + ": not found");
            return 1;
        }
        return 0;
    }

    private void println(OutputStream out, String msg) {
        try { out.write((msg + "\n").getBytes()); out.flush(); } catch (IOException ignored) {}
    }

    private void printErr(OutputStream err, String msg) {
        try { err.write((msg + "\n").getBytes()); err.flush(); } catch (IOException ignored) {}
    }
}
