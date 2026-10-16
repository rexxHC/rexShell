package com.jshell.parser.ast;

/**
 * Marker interface for all AST nodes produced by the {@link com.jshell.parser.Parser}.
 *
 * <p>The executor walks a tree of {@code Node} instances to carry out the shell's semantics.
 * All node types are sealed records or classes that implement this interface.
 */
public sealed interface Node
        permits SimpleCommand, Pipeline, AndOr, CommandList,
                Redirection, Subshell, GroupCommand, Assignment {
}
