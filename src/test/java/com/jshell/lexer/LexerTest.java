package com.jshell.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    private List<Token> lex(String input) {
        return new Lexer(input).tokenize();
    }

    @Test
    void simpleWords() {
        List<Token> tokens = lex("echo hello world");
        assertEquals(TokenType.WORD, tokens.get(0).type());
        assertEquals("echo",  tokens.get(0).value());
        assertEquals("hello", tokens.get(1).value());
        assertEquals("world", tokens.get(2).value());
        assertTrue(tokens.get(3).isEof());
    }

    @Test
    void pipeToken() {
        List<Token> tokens = lex("ls | grep foo");
        assertEquals(TokenType.PIPE, tokens.get(1).type());
    }

    @Test
    void logicalAndOr() {
        List<Token> and = lex("a && b");
        assertEquals(TokenType.AND, and.get(1).type());

        List<Token> or = lex("a || b");
        assertEquals(TokenType.OR, or.get(1).type());
    }

    @Test
    void redirectOut() {
        List<Token> tokens = lex("echo hi > out.txt");
        assertEquals(TokenType.REDIRECT_OUT, tokens.get(2).type());
        assertEquals("out.txt", tokens.get(3).value());
    }

    @Test
    void redirectAppend() {
        List<Token> tokens = lex("echo hi >> out.txt");
        assertEquals(TokenType.REDIRECT_APPEND, tokens.get(2).type());
    }

    @Test
    void redirectStderr() {
        List<Token> tokens = lex("cmd 2> err.txt");
        assertEquals(TokenType.REDIRECT_ERR, tokens.get(1).type());
    }

    @Test
    void redirectErrToOut() {
        List<Token> tokens = lex("cmd 2>&1");
        assertEquals(TokenType.REDIRECT_ERR_TO_OUT, tokens.get(1).type());
    }

    @Test
    void hereDoc() {
        List<Token> tokens = lex("cat << EOF");
        assertEquals(TokenType.HERE_DOC, tokens.get(1).type());
    }

    @Test
    void hereString() {
        List<Token> tokens = lex("cat <<< hello");
        assertEquals(TokenType.HERE_STRING, tokens.get(1).type());
    }

    @Test
    void singleQuotes() {
        List<Token> tokens = lex("echo 'hello world'");
        // Single-quoted content becomes one WORD without quotes
        assertEquals("hello world", tokens.get(1).value());
    }

    @Test
    void doubleQuotes() {
        List<Token> tokens = lex("echo \"hello world\"");
        // Double-quoted content retains quotes (Expander will process $-expansions)
        assertEquals("\"hello world\"", tokens.get(1).value());
    }

    @Test
    void unterminatedSingleQuoteThrows() {
        assertThrows(LexerException.class, () -> lex("echo 'unterminated"));
    }

    @Test
    void unterminatedDoubleQuoteThrows() {
        assertThrows(LexerException.class, () -> lex("echo \"unterminated"));
    }

    @Test
    void assignment() {
        List<Token> tokens = lex("FOO=bar");
        assertEquals(TokenType.ASSIGNMENT_WORD, tokens.get(0).type());
        assertEquals("FOO=bar", tokens.get(0).value());
    }

    @Test
    void commentIsSkipped() {
        List<Token> tokens = lex("echo hi # this is a comment");
        assertEquals(TokenType.WORD, tokens.get(0).type());
        assertEquals(TokenType.WORD, tokens.get(1).type());
        // Next should be EOF (comment consumed)
        assertEquals(TokenType.EOF, tokens.get(2).type());
    }

    @Test
    void semicolonSeparator() {
        List<Token> tokens = lex("cmd1; cmd2");
        assertEquals(TokenType.SEMI, tokens.get(1).type());
    }

    @Test
    void backgroundAmp() {
        List<Token> tokens = lex("sleep 10 &");
        assertEquals(TokenType.AMP, tokens.get(2).type());
    }

    @Test
    void backslashLineContinuation() {
        List<Token> tokens = lex("echo hello\\\nworld");
        // The word should merge across the continuation
        assertEquals("helloworld", tokens.get(1).value());
    }

    @Test
    void dollarVarInWord() {
        List<Token> tokens = lex("echo $HOME");
        assertEquals("$HOME", tokens.get(1).value());
        assertEquals(TokenType.WORD, tokens.get(1).type());
    }

    @Test
    void commandSubstitution() {
        List<Token> tokens = lex("echo $(pwd)");
        assertEquals("$(pwd)", tokens.get(1).value());
    }

    @Test
    void subshellParens() {
        List<Token> tokens = lex("(echo hi)");
        assertEquals(TokenType.LPAREN, tokens.get(0).type());
        assertEquals("echo", tokens.get(1).value());
        assertEquals("hi", tokens.get(2).value());
        assertEquals(TokenType.RPAREN, tokens.get(3).type());
    }
}
