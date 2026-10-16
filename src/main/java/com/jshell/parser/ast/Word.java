package com.jshell.parser.ast;

import com.jshell.lexer.Token;
import com.jshell.lexer.WordPart;

import java.util.List;

/**
 * A shell word AST node, composed of one or more {@link WordPart} segments
 * retaining quoting context.
 *
 * <p>For example, in {@code foo"bar$BAZ"'qux'}, the word consists of:
 * <ul>
 *   <li>{@code BARE("foo")}</li>
 *   <li>{@code DOUBLE_QUOTED("bar$BAZ")}</li>
 *   <li>{@code SINGLE_QUOTED("qux")}</li>
 * </ul>
 *
 * @param text  The default unquoted/raw text value.
 * @param parts The quoting breakdown of this word.
 */
public record Word(String text, List<WordPart> parts) {

    /** Creates a bare word with a single BARE part. */
    public static Word bare(String text) {
        return new Word(text, List.of(new WordPart(WordPart.PartType.BARE, text)));
    }

    /** Creates a Word from a token. */
    public static Word fromToken(Token token) {
        if (token.parts() != null && !token.parts().isEmpty()) {
            return new Word(token.value(), token.parts());
        }
        return bare(token.value());
    }

    /** Returns true if this word has any parts with BARE type. */
    public boolean hasBareParts() {
        return parts.stream().anyMatch(p -> p.type() == WordPart.PartType.BARE);
    }

    /** Returns true if this word has any parts that can undergo variable expansion. */
    public boolean canExpandVariables() {
        return parts.stream().anyMatch(p ->
                p.type() == WordPart.PartType.BARE || p.type() == WordPart.PartType.DOUBLE_QUOTED);
    }

    @Override
    public String toString() {
        return text;
    }
}
