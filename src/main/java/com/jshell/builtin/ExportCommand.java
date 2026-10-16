package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Built-in {@code export} command.
 *
 * <p>Usage: {@code export [-p] [name[=value] ...]}
 * <ul>
 *   <li>{@code export} or {@code export -p} — list all exported variables in format {@code declare -x NAME="VALUE"}</li>
 *   <li>{@code export NAME=value} — set and export the variable</li>
 *   <li>{@code export NAME} — export an existing variable</li>
 * </ul>
 */
public class ExportCommand implements Builtin {

    @Override
    public String name() { return "export"; }

    @Override
    public String usage() { return "export [-p] [name[=value] ...]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        // If no arguments or just "-p", print all exported variables
        if (args.length == 1 || (args.length == 2 && "-p".equals(args[1]))) {
            Map<String, String> exported = env.getExportedVariables();
            exported.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        String escaped = escapeValue(e.getValue());
                        out.println("declare -x " + e.getKey() + "=\"" + escaped + "\"");
                    });
            out.flush();
            return 0;
        }

        int status = 0;
        int start = 1;
        if ("-p".equals(args[1])) {
            start = 2;
        }

        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            int eq = arg.indexOf('=');

            if (eq > 0) {
                String varName = arg.substring(0, eq);
                String varValue = arg.substring(eq + 1);
                if (!isValidIdentifier(varName)) {
                    err.println("jshell: export: '" + arg + "': not a valid identifier");
                    status = 1;
                    continue;
                }
                env.export(varName, varValue);
            } else {
                if (!isValidIdentifier(arg)) {
                    err.println("jshell: export: '" + arg + "': not a valid identifier");
                    status = 1;
                    continue;
                }
                env.export(arg);
            }
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

    private String escapeValue(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("$", "\\$")
                  .replace("`", "\\`");
    }
}
