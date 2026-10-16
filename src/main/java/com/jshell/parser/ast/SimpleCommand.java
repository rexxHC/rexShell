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
 * @param words        The command name (index 0) and its arguments as plain strings.
 *                     Empty if the command consists only of assignments.
 * @param redirections I/O redirections attached to this command.
 * @param wordNodes    The command words as rich {@link Word} objects with {@link com.jshell.lexer.WordPart} context.
 */
public record SimpleCommand(
        List<Assignment> assignments,
        List<String>     words,
        List<Redirection> redirections,
        List<Word>       wordNodes
) implements Node {

    /** Backwards-compatible 3-argument constructor. */
    public SimpleCommand(List<Assignment> assignments, List<String> words, List<Redirection> redirections) {
        this(assignments, words, redirections, words.stream().map(Word::bare).toList());
    }

    /** Factory creating SimpleCommand from rich Word nodes. */
    public static SimpleCommand of(List<Assignment> assignments, List<Word> wordNodes, List<Redirection> redirections) {
        List<String> wordTexts = wordNodes.stream().map(Word::text).toList();
        return new SimpleCommand(assignments, wordTexts, redirections, List.copyOf(wordNodes));
    }

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
