package com.jshell.parser.ast;

import java.util.List;

/**
 * A simple command: an optional list of variable assignments followed by a
 * command word and its arguments, plus any I/O redirections.
 *
 * <p>Examples:
 * <pre>
 *   echo hello world
 *   FOO=bar env
 *   ls -la /tmp > out.txt
 * </pre>
 *
 * @param assignments  Variable assignments that precede the command name
 *                     (e.g., {@code FOO=bar}). May be empty.
 * @param words        The command name (index 0) and its arguments.
 *                     Empty if the command consists only of assignments.
 * @param redirections I/O redirections attached to this command.
 */
public record SimpleCommand(
        List<Assignment> assignments,
        List<String>     words,
        List<Redirection> redirections
) implements Node {

    /** Returns the command name, or {@code null} if this is an assignment-only command. */
    public String name() {
        return words.isEmpty() ? null : words.get(0);
    }

    /** Returns the argument list (command name excluded). */
    public List<String> args() {
        return words.isEmpty() ? List.of() : words.subList(1, words.size());
    }

    /** Returns {@code true} if this node is a pure variable assignment with no command. */
    public boolean isAssignmentOnly() {
        return words.isEmpty() && !assignments.isEmpty();
    }
}
