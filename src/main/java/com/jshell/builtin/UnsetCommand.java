package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Built-in {@code unset} command.
 *
 * <p>Usage: {@code unset [-v] [-f] [name ...]}
 * <ul>
 *   <li>Removes variables from the shell environment.</li>
 *   <li>{@code -v} treats arguments as variable names (default).</li>
 *   <li>{@code -f} treats arguments as function names (reserved for Phase 7).</li>
 * </ul>
 */
public class UnsetCommand implements Builtin {

    @Override
    public String name() { return "unset"; }

    @Override
    public String usage() { return "unset [-v] [-f] [name ...]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        int status = 0;
        int start = 1;
        while (start < args.length && args[start].startsWith("-")) {
            String opt = args[start];
            if ("--".equals(opt)) {
                start++;
                break;
            }
            if ("-v".equals(opt) || "-f".equals(opt)) {
                start++;
            } else {
                err.println("jshell: unset: " + opt + ": invalid option");
                return 2;
            }
        }

        for (int i = start; i < args.length; i++) {
            String name = args[i];
            if (!isValidIdentifier(name)) {
                err.println("jshell: unset: '" + name + "': not a valid identifier");
                status = 1;
                continue;
            }
            env.unset(name);
        }

        return status;
    }

    private boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_') return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }
}
