package com.jshell.expansion;

import com.jshell.environment.Environment;

import java.util.function.BiFunction;

/**
 * Handles command substitution: {@code $(command)} and <code>`command`</code>.
 *
 * <p>Executes the command string in a subshell, captures its standard output,
 * strips any trailing newlines (per POSIX/bash standard), and substitutes the text.
 */
public class CommandSubstitution {

    /**
     * Functional callback invoked to execute a command string in a child environment
     * and return its standard output as a string.
     */
    private final BiFunction<String, Environment, String> commandRunner;

    public CommandSubstitution(BiFunction<String, Environment, String> commandRunner) {
        this.commandRunner = commandRunner;
    }

    /**
     * Expands all {@code $(...)} and <code>`...`</code> command substitutions in the input text.
     */
    public String expand(String text, Environment env) {
        if (text == null || (!text.contains("$(") && !text.contains("`"))) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // $(...) syntax (supports nesting)
            if (c == '$' && i + 1 < len && text.charAt(i + 1) == '(' && (i + 2 >= len || text.charAt(i + 2) != '(')) {
                // Not arithmetic $((
                int depth = 1;
                int start = i + 2;
                int j = start;
                while (j < len && depth > 0) {
                    char d = text.charAt(j);
                    if (d == '\\' && j + 1 < len) {
                        j += 2;
                        continue;
                    }
                    if (d == '$' && j + 1 < len && text.charAt(j + 1) == '(') {
                        depth++;
                        j += 2;
                        continue;
                    }
                    if (d == '(') depth++;
                    if (d == ')') depth--;
                    j++;
                }

                if (depth == 0) {
                    String innerCmd = text.substring(start, j - 1);
                    // Recursively expand any nested command substitutions inside innerCmd first
                    String expandedInner = expand(innerCmd, env);
                    String output = runAndCapture(expandedInner, env);
                    result.append(output);
                    i = j - 1;
                    continue;
                }
            }

            // `...` backtick syntax
            if (c == '`') {
                int start = i + 1;
                int j = start;
                StringBuilder innerCmd = new StringBuilder();
                while (j < len && text.charAt(j) != '`') {
                    char d = text.charAt(j);
                    if (d == '\\' && j + 1 < len) {
                        char next = text.charAt(j + 1);
                        if (next == '$' || next == '`' || next == '\\') {
                            innerCmd.append(next);
                            j += 2;
                            continue;
                        }
                    }
                    innerCmd.append(d);
                    j++;
                }

                if (j < len && text.charAt(j) == '`') {
                    String output = runAndCapture(innerCmd.toString(), env);
                    result.append(output);
                    i = j;
                    continue;
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    private String runAndCapture(String command, Environment env) {
        if (commandRunner == null || command.isBlank()) {
            return "";
        }
        String output = commandRunner.apply(command, env);
        if (output == null) return "";

        // Strip trailing newlines (POSIX requirement)
        int end = output.length();
        while (end > 0 && (output.charAt(end - 1) == '\n' || output.charAt(end - 1) == '\r')) {
            end--;
        }
        return output.substring(0, end);
    }
}
