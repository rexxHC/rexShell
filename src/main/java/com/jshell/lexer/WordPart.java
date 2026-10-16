package com.jshell.lexer;

/**
 * A contiguous slice of a {@link TokenType#WORD} token, retaining its quoting context.
 *
 * <p>For example, in {@code foo"bar$BAZ"'qux'}, the word consists of:
 * <ul>
 *   <li>{@link PartType#BARE} {@code "foo"}</li>
 *   <li>{@link PartType#DOUBLE_QUOTED} {@code "bar$BAZ"}</li>
 *   <li>{@link PartType#SINGLE_QUOTED} {@code "qux"}</li>
 * </ul>
 *
 * <p>This structure allows the Expander (Phase 3) to accurately determine which expansions
 * (tilde, variable, arithmetic, word splitting, globbing) apply to which portions of a word.
 *
 * @param type    The quoting type of this part.
 * @param content The unquoted content of this part (with quotes stripped and ANSI-C escapes resolved).
 */
public record WordPart(PartType type, String content) {

    public enum PartType {
        /** Unquoted text — subject to all expansions, word splitting, and globbing. */
        BARE,

        /** Single-quoted text ('...') — completely literal, no expansion or splitting. */
        SINGLE_QUOTED,

        /** Double-quoted text ("...") — subject to variable/arithmetic/command substitution, but NOT word splitting or globbing. */
        DOUBLE_QUOTED,

        /** ANSI-C quoted text ($'...') — escape sequences resolved, otherwise literal like single quotes. */
        ANSI_C
    }
}
