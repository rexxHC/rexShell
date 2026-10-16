package com.jshell.parser.ast;

/**
 * A logical AND ({@code &&}) or OR ({@code ||}) between two commands/pipelines.
 *
 * <p>Examples:
 * <pre>
 *   make && make install
 *   test -f foo.txt || echo "not found"
 * </pre>
 *
 * @param left     The left-hand operand.
 * @param operator Either {@code &&} or {@code ||}.
 * @param right    The right-hand operand (only executed depending on {@code left}'s exit status).
 */
public record AndOr(Node left, String operator, Node right) implements Node {}
