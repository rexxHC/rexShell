package com.jshell.lexer;

/**
 * A single token produced by the {@link Lexer}.
 *
 * @param type    The type of this token.
 * @param value   The processed string value (quotes already removed / escape sequences resolved
 *                for WORD tokens; raw operator string for operator tokens).
 * @param raw     The original, unprocessed text from the input stream.
 * @param line    1-based line number where this token starts.
 * @param column  1-based column number where this token starts.
 */
public record Token(TokenType type, String value, String raw, int line, int column) {

    /**
     * Convenience factory for tokens where the raw and value are identical
     * (e.g., operators that need no processing).
     */
    public static Token of(TokenType type, String value, int line, int column) {
        return new Token(type, value, value, line, column);
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
