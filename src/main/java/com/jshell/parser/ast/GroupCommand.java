package com.jshell.parser.ast;

import java.util.List;

/**
 * A brace group: commands executed in the <em>current</em> shell environment — {@code { cmd1; cmd2; }}.
 *
 * <p>Unlike a {@link Subshell}, variable changes DO propagate to the surrounding scope.
 *
 * @param body        The command list.
 * @param redirections I/O redirections applied to the group as a whole.
 */
public record GroupCommand(Node body, List<Redirection> redirections) implements Node {}
