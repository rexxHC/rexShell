package com.jshell.parser.ast;

import java.util.List;

/**
 * A pipeline: two or more commands connected by {@code |}.
 *
 * <p>Example: {@code ls -la | grep java | wc -l}
 *
 * @param negate   If {@code true}, the exit status of the pipeline is logically negated
 *                 (the {@code !} prefix was present).
 * @param commands The sequence of commands in the pipeline (at least 2).
 */
public record Pipeline(boolean negate, List<Node> commands) implements Node {}
