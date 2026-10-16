package com.jshell.lexer;

/**
 * Thrown when the {@link Lexer} encounters an unrecoverable lexical error
 * (e.g., an unterminated quoted string).
 */
public class LexerException extends RuntimeException {

    private final int line;
    private final int column;

    public LexerException(String message, int line, int column) {
        super(String.format("jshell: line %d col %d: %s", line, column, message));
        this.line   = line;
        this.column = column;
    }

    public int getLine()   { return line;   }
    public int getColumn() { return column; }
}
