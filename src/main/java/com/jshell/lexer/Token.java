package com.jshell.lexer;

import java.util.List;

/**
 * A single token produced by the {@link Lexer}.
 *
 * @param type    The type of this token.
 * @param value   The processed string value (quotes stripped, escapes resolved).
 * @param raw     The original, unprocessed text from the input stream.
 * @param line    1-based line number where this token starts.
 * @param column  1-based column number where this token starts.
 * @param parts   The breakdown of word parts (for WORD and ASSIGNMENT_WORD tokens).
 */
public record Token(
        TokenType type,
        String value,
        String raw,
        int line,
        int column,
        List<WordPart> parts
) {

    /** 5-argument constructor for backwards compatibility where parts are not needed. */
    public Token(TokenType type, String value, String raw, int line, int column) {
        this(type, value, raw, line, column, List.of());
    }

    /**
     * Convenience factory for tokens where raw and value are identical
     * (e.g., operators that need no processing).
     */
    public static Token of(TokenType type, String value, int line, int column) {
        return new Token(type, value, value, line, column, List.of());
    }

    /**
     * Convenience factory for tokens with different raw and value.
     */
    public static Token of(TokenType type, String value, String raw, int line, int column) {
        return new Token(type, value, raw, line, column, List.of());
    }

    /**
     * Factory for WORD tokens with parts.
     */
    public static Token word(String value, String raw, int line, int column, List<WordPart> parts) {
        return new Token(TokenType.WORD, value, raw, line, column, List.copyOf(parts));
    }

    /**
     * Factory for ASSIGNMENT_WORD tokens with parts.
     */
    public static Token assignment(String value, String raw, int line, int column, List<WordPart> parts) {
        return new Token(TokenType.ASSIGNMENT_WORD, value, raw, line, column, List.copyOf(parts));
    }

    /** Returns {@code true} if this token is the EOF sentinel. */
    public boolean isEof() {
        return type == TokenType.EOF;
    }

    /** Returns {@code true} if this token is a NEWLINE or EOF. */
    public boolean isTerminator() {
        return type == TokenType.NEWLINE || type == TokenType.EOF;
    }

    @Override
    public String toString() {
        return "Token[" + type + ", \"" + value + "\"]";
    }
}
