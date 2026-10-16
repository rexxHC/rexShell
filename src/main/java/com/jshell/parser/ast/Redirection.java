package com.jshell.parser.ast;

import com.jshell.lexer.TokenType;

/**
 * An I/O redirection attached to a command.
 *
 * <p>Examples:
 * <pre>
 *   > file       (stdout truncate)
 *   >> file      (stdout append)
 *   < file       (stdin)
 *   2> file      (stderr truncate)
 *   2>&amp;1    (stderr to stdout)
 *   &gt; file      (stdout+stderr)
 *   << EOF       (here-doc)
 *   <<< "str"    (here-string)
 * </pre>
 *
 * @param type   The redirect operator type (from {@link TokenType}).
 * @param fd     The explicit file descriptor number, or {@code -1} if not specified.
 * @param target The target filename, FD number string, or here-doc delimiter.
 */
public record Redirection(TokenType type, int fd, String target) implements Node {}
