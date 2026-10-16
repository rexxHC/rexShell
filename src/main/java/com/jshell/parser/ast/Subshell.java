package com.jshell.parser.ast;

import java.util.List;

/**
 * A subshell: commands executed in a child environment — {@code (cmd1; cmd2)}.
 *
 * <p>Variable assignments and directory changes inside do not propagate to the parent.
 *
 * @param body        The command list executed inside the subshell.
 * @param redirections I/O redirections applied to the subshell as a whole.
 */
public record Subshell(Node body, List<Redirection> redirections) implements Node {}
