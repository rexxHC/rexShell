package com.jshell.parser.ast;

/**
 * A variable assignment: {@code NAME=value}.
 *
 * <p>Assignments can appear:
 * <ul>
 *   <li>Before a command (temporary environment for that command)</li>
 *   <li>As a standalone statement (sets a shell variable)</li>
 *   <li>With {@code export} to create environment variables</li>
 * </ul>
 *
 * @param name  The variable name.
 * @param value The raw value string (may contain expansion tokens resolved later by the Expander).
 */
public record Assignment(String name, String value) implements Node {}
