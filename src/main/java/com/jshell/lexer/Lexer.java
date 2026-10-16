package com.jshell.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer (tokenizer) for JShell.
 *
 * <p>Converts a raw input string into a list of {@link Token}s.
 * Handles:
 * <ul>
 *   <li>Basic word tokenization with contiguous word merging (e.g. {@code foo"bar"'baz'})</li>
 *   <li>Single quotes {@code '...'}: literal content, no expansion</li>
 *   <li>Double quotes {@code "..."}: allows expansions, resolves escapes ({@code \"}, {@code \\}, {@code \$}, etc.)</li>
 *   <li>ANSI-C quotes {@code $'...'}: resolves {@code \n}, {@code \t}, {@code \xHH}, {@code \0NNN}, unicode, etc.</li>
 *   <li>Backslash escaping outside quotes ({@code \ } for literal spaces, line continuations)</li>
 *   <li>Empty quoted strings ({@code ""} or {@code ''}) preserved as valid WORD tokens</li>
 *   <li>All shell control and I/O redirection operators</li>
 *   <li>Variable assignments ({@code NAME=value})</li>
 * </ul>
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

        // Operators starting with '&'
        if (c == '&') {
            return readAmpOperator(tokenLine, tokenCol);
        }

        // Operators starting with '|'
        if (c == '|') {
            return readPipeOperator(tokenLine, tokenCol);
        }

        // Operators starting with '<'
        if (c == '<') {
            return readLtOperator(tokenLine, tokenCol);
        }

        // Operators starting with '>' (not preceded by explicit FD digit)
        if (c == '>') {
            return readGtOperator(tokenLine, tokenCol);
        }

        // Semicolon
        if (c == ';') {
            advance();
            return Token.of(TokenType.SEMI, ";", tokenLine, tokenCol);
        }

        // Parentheses and braces
        if (c == '(') { advance(); return Token.of(TokenType.LPAREN, "(", tokenLine, tokenCol); }
        if (c == ')') { advance(); return Token.of(TokenType.RPAREN, ")", tokenLine, tokenCol); }
        if (c == '{') { advance(); return Token.of(TokenType.LBRACE, "{", tokenLine, tokenCol); }
        if (c == '}') { advance(); return Token.of(TokenType.RBRACE, "}", tokenLine, tokenCol); }

        // Bang
        if (c == '!' && !isWordChar(peekAt(pos + 1))) {
            advance();
            return Token.of(TokenType.BANG, "!", tokenLine, tokenCol);
        }

        // Word (including quoted strings, $(...), assignments, etc.)
        return readWord(tokenLine, tokenCol);
    }

    // -----------------------------------------------------------------------
    // Operator readers
    // -----------------------------------------------------------------------

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

    private Token readPipeOperator(int tl, int tc) {
        advance(); // consume '|'
        if (pos < input.length() && peek() == '|') {
            advance();
            return Token.of(TokenType.OR, "||", tl, tc);
        }
        return Token.of(TokenType.PIPE, "|", tl, tc);
    }

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

    private Token readGtOperator(int tl, int tc) {
        advance(); // consume '>'
        if (pos < input.length() && peek() == '>') {
            advance();
            return Token.of(TokenType.REDIRECT_APPEND, ">>", tl, tc);
        }
        return Token.of(TokenType.REDIRECT_OUT, ">", tl, tc);
    }

    // -----------------------------------------------------------------------
    // Word reader with complete quoting and merging support
    // -----------------------------------------------------------------------

    private Token readWord(int tl, int tc) {
        StringBuilder raw = new StringBuilder();
        StringBuilder value = new StringBuilder();
        List<WordPart> parts = new ArrayList<>();
        StringBuilder barePart = new StringBuilder();

        while (pos < input.length()) {
            char c = peek();

            // Whitespace or newline terminates a word
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                break;
            }

            // Unquoted operator character terminates a word (if we have read anything)
            if (isOperatorStart(c) && (raw.length() > 0 || !parts.isEmpty())) {
                break;
            }

            // Digit followed by '>' or '<' at the very start of a word -> redirect with explicit FD
            if (raw.length() == 0 && parts.isEmpty() && Character.isDigit(c)) {
                int savedPos = pos;
                int savedLine = line;
                int savedCol = col;
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
                        if (pos < input.length() && peek() == '>') {
                            advance();
                            return Token.of(TokenType.REDIRECT_FD_OUT, fdStr + ">>", tl, tc);
                        }
                        if (pos < input.length() && peek() == '&') {
                            advance();
                            StringBuilder target = new StringBuilder();
                            while (pos < input.length() && Character.isDigit(peek())) {
                                target.append(peek());
                                advance();
                            }
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
                    // Backtrack and treat digits as normal characters
                    pos = savedPos;
                    line = savedLine;
                    col = savedCol;
                    // c is still the current digit
                }
            }

            // ANSI-C Quoting: $'...'
            if (c == '$' && pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                flushBare(parts, barePart);
                raw.append("$'");
                advance(); // consume '$'
                advance(); // consume '\''
                StringBuilder ansiContent = new StringBuilder();
                while (pos < input.length() && peek() != '\'') {
                    char d = peek();
                    if (d == '\\' && pos + 1 < input.length()) {
                        raw.append(d);
                        advance(); // consume '\'
                        char escaped = peek();
                        raw.append(escaped);
                        advance(); // consume escaped char
                        String resolved = resolveAnsiCEscape(escaped, raw);
                        ansiContent.append(resolved);
                        continue;
                    }
                    raw.append(d);
                    ansiContent.append(d);
                    advance();
                }
                if (pos >= input.length()) {
                    throw new LexerException("Unterminated ANSI-C quote", tl, tc);
                }
                raw.append('\'');
                advance(); // consume closing '\''
                value.append(ansiContent);
                parts.add(new WordPart(WordPart.PartType.ANSI_C, ansiContent.toString()));
                continue;
            }

            // Single-quoted string: '...'
            if (c == '\'') {
                flushBare(parts, barePart);
                raw.append(c);
                advance(); // consume opening '\''
                StringBuilder singleContent = new StringBuilder();
                while (pos < input.length() && peek() != '\'') {
                    char d = peek();
                    singleContent.append(d);
                    raw.append(d);
                    advance();
                }
                if (pos >= input.length()) {
                    throw new LexerException("Unterminated single quote", tl, tc);
                }
                raw.append('\'');
                advance(); // consume closing '\''
                value.append(singleContent);
                parts.add(new WordPart(WordPart.PartType.SINGLE_QUOTED, singleContent.toString()));
                continue;
            }

            // Double-quoted string: "..."
            if (c == '"') {
                flushBare(parts, barePart);
                raw.append(c);
                advance(); // consume opening '"'
                StringBuilder doubleContent = new StringBuilder();
                while (pos < input.length()) {
                    char d = peek();
                    if (d == '"') break;
                    if (d == '\\' && pos + 1 < input.length()) {
                        char escaped = input.charAt(pos + 1);
                        if (escaped == '"' || escaped == '\\' || escaped == '$'
                                || escaped == '`' || escaped == '\n') {
                            raw.append('\\').append(escaped);
                            if (escaped == '\n') {
                                // Line continuation inside double quotes
                                advance(); advance();
                                line++; col = 1;
                            } else {
                                doubleContent.append(escaped);
                                advance(); advance();
                            }
                            continue;
                        }
                    }
                    raw.append(d);
                    doubleContent.append(d);
                    advance();
                }
                if (pos >= input.length()) {
                    throw new LexerException("Unterminated double quote", tl, tc);
                }
                raw.append('"');
                advance(); // consume closing '"'
                value.append(doubleContent);
                parts.add(new WordPart(WordPart.PartType.DOUBLE_QUOTED, doubleContent.toString()));
                continue;
            }

            // Backslash outside quotes: escape next char (e.g. '\ ' for literal space)
            if (c == '\\') {
                raw.append(c);
                advance();
                if (pos >= input.length()) break;
                char escaped = peek();
                if (escaped == '\n') {
                    // Line continuation outside quotes: skip both newline and backslash
                    raw.append(escaped);
                    advance();
                    line++;
                    col = 1;
                } else {
                    raw.append(escaped);
                    value.append(escaped);
                    // Treat escaped char as literal single-quoted content so expansions don't touch it
                    flushBare(parts, barePart);
                    parts.add(new WordPart(WordPart.PartType.SINGLE_QUOTED, String.valueOf(escaped)));
                    advance();
                }
                continue;
            }

            // Command substitution $(...) or ${...} outside quotes
            if (c == '$' || c == '`') {
                raw.append(c);
                value.append(c);
                barePart.append(c);
                advance();

                if (c == '$' && pos < input.length() && peek() == '(') {
                    raw.append('(');
                    value.append('(');
                    barePart.append('(');
                    advance();
                    appendBalanced('(', ')', raw, value, barePart);
                } else if (c == '$' && pos < input.length() && peek() == '{') {
                    raw.append('{');
                    value.append('{');
                    barePart.append('{');
                    advance();
                    appendBalanced('{', '}', raw, value, barePart);
                } else if (c == '`') {
                    while (pos < input.length()) {
                        char d = peek();
                        if (d == '`') {
                            raw.append(d); value.append(d); barePart.append(d);
                            advance();
                            break;
                        }
                        if (d == '\\' && pos + 1 < input.length()) {
                            raw.append(d).append(input.charAt(pos + 1));
                            value.append(d).append(input.charAt(pos + 1));
                            barePart.append(d).append(input.charAt(pos + 1));
                            advance(); advance();
                            continue;
                        }
                        raw.append(d); value.append(d); barePart.append(d);
                        advance();
                    }
                }
                continue;
            }

            // Normal unquoted character
            raw.append(c);
            value.append(c);
            barePart.append(c);
            advance();
        }

        flushBare(parts, barePart);

        String rawStr = raw.toString();
        String valueStr = value.toString();

        TokenType type = isAssignment(valueStr) ? TokenType.ASSIGNMENT_WORD : TokenType.WORD;
        return new Token(type, valueStr, rawStr, tl, tc, parts);
    }

    private void flushBare(List<WordPart> parts, StringBuilder barePart) {
        if (barePart.length() > 0) {
            parts.add(new WordPart(WordPart.PartType.BARE, barePart.toString()));
            barePart.setLength(0);
        }
    }

    /**
     * Resolves an ANSI-C escape sequence after the initial backslash.
     */
    private String resolveAnsiCEscape(char first, StringBuilder raw) {
        return switch (first) {
            case 'a' -> "\u0007";
            case 'b' -> "\b";
            case 'e', 'E' -> "\u001B";
            case 'f' -> "\f";
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            case 'v' -> "\u000B";
            case '\\' -> "\\";
            case '\'' -> "'";
            case '"' -> "\"";
            case '?' -> "?";
            case 'c' -> {
                if (pos < input.length()) {
                    char c = peek();
                    raw.append(c);
                    advance();
                    yield String.valueOf((char) (Character.toUpperCase(c) & 0x1F));
                }
                yield "c";
            }
            case '0', '1', '2', '3', '4', '5', '6', '7' -> {
                StringBuilder octal = new StringBuilder().append(first);
                while (octal.length() < 3 && pos < input.length()) {
                    char c = peek();
                    if (c >= '0' && c <= '7') {
                        octal.append(c);
                        raw.append(c);
                        advance();
                    } else {
                        break;
                    }
                }
                int code = Integer.parseInt(octal.toString(), 8);
                yield String.valueOf((char) code);
            }
            case 'x' -> {
                StringBuilder hex = new StringBuilder();
                while (hex.length() < 2 && pos < input.length()) {
                    char c = peek();
                    if (isHex(c)) {
                        hex.append(c);
                        raw.append(c);
                        advance();
                    } else {
                        break;
                    }
                }
                if (hex.length() > 0) {
                    int code = Integer.parseInt(hex.toString(), 16);
                    yield String.valueOf((char) code);
                }
                yield "x";
            }
            case 'u' -> {
                StringBuilder hex = new StringBuilder();
                while (hex.length() < 4 && pos < input.length()) {
                    char c = peek();
                    if (isHex(c)) {
                        hex.append(c);
                        raw.append(c);
                        advance();
                    } else {
                        break;
                    }
                }
                if (hex.length() > 0) {
                    int code = Integer.parseInt(hex.toString(), 16);
                    yield String.valueOf((char) code);
                }
                yield "u";
            }
            case 'U' -> {
                StringBuilder hex = new StringBuilder();
                while (hex.length() < 8 && pos < input.length()) {
                    char c = peek();
                    if (isHex(c)) {
                        hex.append(c);
                        raw.append(c);
                        advance();
                    } else {
                        break;
                    }
                }
                if (hex.length() > 0) {
                    int code = Integer.parseInt(hex.toString(), 16);
                    yield new String(Character.toChars(code));
                }
                yield "U";
            }
            default -> "\\" + first;
        };
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void appendBalanced(char open, char close, StringBuilder raw, StringBuilder value, StringBuilder bare) {
        int depth = 1;
        while (pos < input.length() && depth > 0) {
            char d = peek();
            raw.append(d);
            value.append(d);
            bare.append(d);
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

    private boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

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
