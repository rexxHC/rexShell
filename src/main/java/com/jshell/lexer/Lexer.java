package com.jshell.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer (tokenizer) for JShell.
 *
 * <p>Converts a raw input string into a list of {@link Token}s.
 * This Phase 1 implementation handles:
 * <ul>
 *   <li>Basic word tokenization</li>
 *   <li>All shell operator recognition ({@code |}, {@code ||}, {@code &&}, {@code ;},
 *       {@code &}, {@code (}, {@code )}, {@code {}, {@code }}, {@code !})</li>
 *   <li>I/O redirect operators ({@code >}, {@code >>}, {@code <}, {@code <<},
 *       {@code <<<}, {@code 2>}, {@code 2>>}, {@code &>}, {@code &>>},
 *       {@code 2>&1}, {@code 1>&2}, {@code n>file}, {@code n<file})</li>
 *   <li>Single-quoted strings (literal content, Phase 2 full quoting)</li>
 *   <li>Double-quoted strings (basic grouping, expansion handled in Expander)</li>
 *   <li>Variable assignment detection ({@code NAME=value})</li>
 *   <li>Newline and EOF</li>
 * </ul>
 *
 * <p>Quoting details (single and double quotes, backslash escaping) are
 * handled here at the token level — the resulting token {@code value} already
 * has outer quote characters removed and escape sequences resolved so that
 * downstream stages receive clean strings.
 */
public class Lexer {

    private final String input;
    private int pos;    // current character position
    private int line;   // current line (1-based)
    private int col;    // current column (1-based)

    public Lexer(String input) {
        this.input = input;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Tokenizes the entire input and returns all tokens, ending with an EOF token.
     *
     * @throws LexerException if an unterminated quote or other lexical error is detected.
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token token = nextToken();
            tokens.add(token);
            if (token.isEof()) {
                break;
            }
        }
        return tokens;
    }

    // -----------------------------------------------------------------------
    // Core tokenizer loop
    // -----------------------------------------------------------------------

    private Token nextToken() {
        skipWhitespace();

        if (pos >= input.length()) {
            return Token.of(TokenType.EOF, "", line, col);
        }

        int tokenLine = line;
        int tokenCol  = col;
        char c = peek();

        // Newline
        if (c == '\n') {
            advance();
            return Token.of(TokenType.NEWLINE, "\n", tokenLine, tokenCol);
        }

        // Comment: skip to end of line
        if (c == '#') {
            skipComment();
            return nextToken();
        }

        // Operators that start with '&'
        if (c == '&') {
            return readAmpOperator(tokenLine, tokenCol);
        }

        // Operators that start with '|'
        if (c == '|') {
            return readPipeOperator(tokenLine, tokenCol);
        }

        // Operators that start with '<'
        if (c == '<') {
            return readLtOperator(tokenLine, tokenCol);
        }

        // Operators that start with '>' (plain, not preceded by digit — handled below)
        if (c == '>') {
            return readGtOperator(tokenLine, tokenCol);
        }

        // Semicolon
        if (c == ';') {
            advance();
            return Token.of(TokenType.SEMI, ";", tokenLine, tokenCol);
        }

        // Parentheses and braces
        if (c == '(') { advance(); return Token.of(TokenType.LPAREN,  "(", tokenLine, tokenCol); }
        if (c == ')') { advance(); return Token.of(TokenType.RPAREN,  ")", tokenLine, tokenCol); }
        if (c == '{') { advance(); return Token.of(TokenType.LBRACE,  "{", tokenLine, tokenCol); }
        if (c == '}') { advance(); return Token.of(TokenType.RBRACE,  "}", tokenLine, tokenCol); }

        // Bang
        if (c == '!' && !isWordChar(peekAt(pos + 1))) {
            advance();
            return Token.of(TokenType.BANG, "!", tokenLine, tokenCol);
        }

        // Word (including quoted strings, $(...), etc.)
        return readWord(tokenLine, tokenCol);
    }

    // -----------------------------------------------------------------------
    // Operator readers
    // -----------------------------------------------------------------------

    /** Handles: {@code &}, {@code &&}, {@code &>}, {@code &>>} */
    private Token readAmpOperator(int tl, int tc) {
        advance(); // consume '&'
        if (pos < input.length()) {
            char next = peek();
            if (next == '&') {
                advance();
                return Token.of(TokenType.AND, "&&", tl, tc);
            }
            if (next == '>') {
                advance();
                if (pos < input.length() && peek() == '>') {
                    advance();
                    return Token.of(TokenType.REDIRECT_ALL_APPEND, "&>>", tl, tc);
                }
                return Token.of(TokenType.REDIRECT_ALL, "&>", tl, tc);
            }
        }
        return Token.of(TokenType.AMP, "&", tl, tc);
    }

    /** Handles: {@code |}, {@code ||} */
    private Token readPipeOperator(int tl, int tc) {
        advance(); // consume '|'
        if (pos < input.length() && peek() == '|') {
            advance();
            return Token.of(TokenType.OR, "||", tl, tc);
        }
        return Token.of(TokenType.PIPE, "|", tl, tc);
    }

    /** Handles: {@code <}, {@code <<}, {@code <<<} */
    private Token readLtOperator(int tl, int tc) {
        advance(); // consume '<'
        if (pos < input.length() && peek() == '<') {
            advance();
            if (pos < input.length() && peek() == '<') {
                advance();
                return Token.of(TokenType.HERE_STRING, "<<<", tl, tc);
            }
            return Token.of(TokenType.HERE_DOC, "<<", tl, tc);
        }
        return Token.of(TokenType.REDIRECT_IN, "<", tl, tc);
    }

    /**
     * Handles: {@code >}, {@code >>}.
     * Note: digit-prefixed redirects ({@code 2>}, {@code 1>}) and
     * FD duplications ({@code 2>&1}) are handled in {@link #readWord}
     * when a digit is detected at the start of a potential word.
     */
    private Token readGtOperator(int tl, int tc) {
        advance(); // consume '>'
        if (pos < input.length() && peek() == '>') {
            advance();
            return Token.of(TokenType.REDIRECT_APPEND, ">>", tl, tc);
        }
        return Token.of(TokenType.REDIRECT_OUT, ">", tl, tc);
    }

    // -----------------------------------------------------------------------
    // Word reader
    // -----------------------------------------------------------------------

    /**
     * Reads a WORD or ASSIGNMENT_WORD token.
     *
     * <p>A word is terminated by whitespace, a newline, or an unquoted
     * operator character. Quoted regions and {@code $()} are included
     * verbatim (expansion happens later in the Expander).
     */
    private Token readWord(int tl, int tc) {
        StringBuilder raw   = new StringBuilder();
        StringBuilder value = new StringBuilder();

        while (pos < input.length()) {
            char c = peek();

            // Whitespace or newline terminates a word
            if (c == ' ' || c == '\t' || c == '\n') break;

            // Unquoted operator characters terminate a word
            if (isOperatorStart(c) && raw.length() > 0) break;

            // Single-quoted string: '...' — content is literal
            if (c == '\'') {
                raw.append(c);
                advance();
                StringBuilder content = new StringBuilder();
                while (pos < input.length() && peek() != '\'') {
                    content.append(peek());
                    raw.append(peek());
                    advance();
                }
                if (pos >= input.length()) {
                    throw new LexerException("Unterminated single quote", tl, tc);
                }
                raw.append('\'');
                advance(); // closing quote
                value.append(content);
                continue;
            }

            // Double-quoted string: "..." — keep the quotes so the Expander
            // knows this region is double-quoted (expansions apply, word splitting does not)
            if (c == '"') {
                raw.append(c);
                value.append(c);
                advance();
                while (pos < input.length()) {
                    char d = peek();
                    if (d == '"') break;
                    if (d == '\\' && pos + 1 < input.length()) {
                        char escaped = input.charAt(pos + 1);
                        // Inside double quotes only \", \\, \$, \`, \<newline> are special
                        if (escaped == '"' || escaped == '\\' || escaped == '$'
                                || escaped == '`' || escaped == '\n') {
                            raw.append('\\').append(escaped);
                            if (escaped == '\n') {
                                // line continuation — skip both chars
                            } else {
                                value.append(escaped);
                            }
                            advance(); advance();
                            continue;
                        }
                    }
                    raw.append(d);
                    value.append(d);
                    advance();
                }
                if (pos >= input.length()) {
                    throw new LexerException("Unterminated double quote", tl, tc);
                }
                raw.append('"');
                value.append('"');
                advance(); // closing quote
                continue;
            }

            // Backslash outside quotes: escape next char (line continuation if \<newline>)
            if (c == '\\') {
                raw.append(c);
                advance();
                if (pos >= input.length()) break;
                char escaped = peek();
                if (escaped == '\n') {
                    // Line continuation: skip the newline, continue same word
                    raw.append(escaped);
                    advance();
                    line++;
                    col = 1;
                } else {
                    raw.append(escaped);
                    value.append(escaped);
                    advance();
                }
                continue;
            }

            // Digit followed by '>' or '<' at start of word → redirect with explicit FD
            if (raw.length() == 0 && Character.isDigit(c)) {
                // Look ahead to see if next non-digit is > or <
                int savedPos = pos;
                StringBuilder digits = new StringBuilder();
                while (pos < input.length() && Character.isDigit(peek())) {
                    digits.append(peek());
                    advance();
                }
                if (pos < input.length() && (peek() == '>' || peek() == '<')) {
                    char op = peek();
                    advance();
                    String fdStr = digits.toString();
                    if (op == '>') {
                        // Check for 2>&1 or 2>>
                        if (pos < input.length() && peek() == '>') {
                            advance();
                            return Token.of(TokenType.REDIRECT_FD_OUT, fdStr + ">>", tl, tc);
                        }
                        if (pos < input.length() && peek() == '&') {
                            advance();
                            // Read the target FD
                            StringBuilder target = new StringBuilder();
                            while (pos < input.length() && Character.isDigit(peek())) {
                                target.append(peek());
                                advance();
                            }
                            // e.g. "2>&1" stored as value="2>&1"
                            // Special well-known forms:
                            String full = fdStr + ">&" + target;
                            if (fdStr.equals("2") && target.toString().equals("1")) {
                                return Token.of(TokenType.REDIRECT_ERR_TO_OUT, full, tl, tc);
                            }
                            if (fdStr.equals("1") && target.toString().equals("2")) {
                                return Token.of(TokenType.REDIRECT_OUT_TO_ERR, full, tl, tc);
                            }
                            return Token.of(TokenType.REDIRECT_FD_OUT, full, tl, tc);
                        }
                        if (fdStr.equals("2")) {
                            return Token.of(TokenType.REDIRECT_ERR, "2>", tl, tc);
                        }
                        if (fdStr.equals("1")) {
                            return Token.of(TokenType.REDIRECT_OUT, "1>", tl, tc);
                        }
                        return Token.of(TokenType.REDIRECT_FD_OUT, fdStr + ">", tl, tc);
                    } else { // '<'
                        if (fdStr.equals("1")) {
                            return Token.of(TokenType.REDIRECT_IN, "1<", tl, tc);
                        }
                        return Token.of(TokenType.REDIRECT_FD_IN, fdStr + "<", tl, tc);
                    }
                } else {
                    // Not a redirect — backtrack and treat digits as word chars
                    pos = savedPos;
                    col = tc + (pos - savedPos); // approximate reset
                }
            }

            // Command substitution $(...) or ${ } — keep verbatim for Expander
            if (c == '$' || c == '`') {
                raw.append(c);
                value.append(c);
                advance();
                if (c == '$' && pos < input.length() && peek() == '(') {
                    // Read balanced $(...)
                    raw.append('(');
                    value.append('(');
                    advance();
                    appendBalanced('(', ')', raw, value);
                } else if (c == '$' && pos < input.length() && peek() == '{') {
                    // Read balanced ${...}
                    raw.append('{');
                    value.append('{');
                    advance();
                    appendBalanced('{', '}', raw, value);
                } else if (c == '$' && pos < input.length() && peek() == '(') {
                    // arithmetic $((
                    // handled above by the balanced reader
                }
                // backtick: read until next unescaped backtick
                else if (c == '`') {
                    while (pos < input.length()) {
                        char d = peek();
                        if (d == '`') { raw.append(d); value.append(d); advance(); break; }
                        if (d == '\\' && pos + 1 < input.length()) {
                            raw.append(d).append(input.charAt(pos + 1));
                            value.append(d).append(input.charAt(pos + 1));
                            advance(); advance();
                            continue;
                        }
                        raw.append(d); value.append(d); advance();
                    }
                }
                continue;
            }

            // Regular word character
            raw.append(c);
            value.append(c);
            advance();
        }

        String rawStr   = raw.toString();
        String valueStr = value.toString();

        // Detect assignment: NAME=... at start of token, NAME must be [A-Za-z_][A-Za-z0-9_]*
        TokenType type = isAssignment(valueStr) ? TokenType.ASSIGNMENT_WORD : TokenType.WORD;

        return new Token(type, valueStr, rawStr, tl, tc);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Reads characters until the closing delimiter (tracking nesting), appending to both buffers.
     */
    private void appendBalanced(char open, char close, StringBuilder raw, StringBuilder value) {
        int depth = 1;
        while (pos < input.length() && depth > 0) {
            char d = peek();
            raw.append(d);
            value.append(d);
            advance();
            if (d == open)  depth++;
            if (d == close) depth--;
        }
    }

    private char peek() {
        return input.charAt(pos);
    }

    private char peekAt(int index) {
        return (index < input.length()) ? input.charAt(index) : '\0';
    }

    private void advance() {
        if (pos < input.length()) {
            if (input.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }

    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else {
                break;
            }
        }
    }

    private void skipComment() {
        while (pos < input.length() && peek() != '\n') {
            advance();
        }
    }

    /**
     * Returns true if the character can start a shell operator (and thus terminate a word
     * when encountered unquoted).
     */
    private boolean isOperatorStart(char c) {
        return switch (c) {
            case '|', '&', ';', '<', '>', '(', ')', '{', '}' -> true;
            default -> false;
        };
    }

    private boolean isWordChar(char c) {
        if (c == '\0') return false;
        return !Character.isWhitespace(c) && !isOperatorStart(c);
    }

    /**
     * Returns true if the value looks like a shell variable assignment: {@code NAME=...}.
     * The name must match {@code [A-Za-z_][A-Za-z0-9_]*}.
     */
    private boolean isAssignment(String value) {
        int eq = value.indexOf('=');
        if (eq <= 0) return false;
        String name = value.substring(0, eq);
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') return false;
        for (int i = 1; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') return false;
        }
        return true;
    }
}
