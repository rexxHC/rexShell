package com.jshell.parser.ast;

import java.util.List;

/**
 * A list of commands separated by {@code ;} or {@code &}.
 *
 * <p>Examples:
 * <pre>
 *   cmd1; cmd2; cmd3
 *   sleep 10 &amp;
 * </pre>
 *
 * @param commands   The ordered list of commands.
 * @param separators The separator that follows each command (same length as {@code commands}).
 *                   Each element is either {@code ";"} (sequential) or {@code "&"} (background).
 *                   The last element represents the terminator of the whole list.
 */
public record CommandList(List<Node> commands, List<String> separators) implements Node {}
