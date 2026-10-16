package com.jshell.parser;

import com.jshell.lexer.Token;
import com.jshell.lexer.TokenType;
import com.jshell.parser.ast.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for JShell.
 *
 * <p>Converts a flat list of {@link Token}s into an Abstract Syntax Tree rooted at a {@link Node}.
 *
 * <p><b>Grammar (simplified, Phase 1):</b>
 * <pre>
 * list          := and_or ( (';' | '&amp;' | NEWLINE) and_or )* (';' | '&amp;' | NEWLINE)?
 * and_or        := pipeline ( ('&amp;&amp;' | '||') pipeline )*
 * pipeline      := '!'? command ( '|' command )*
 * command       := simple_command | subshell | group_command
 * simple_command:= (ASSIGNMENT_WORD)* WORD* redirect*
 * subshell      := '(' list ')' redirect*
 * group_command := '{' list '}' redirect*
 * redirect      := REDIRECT_* WORD
 * </pre>
 *
 * <p>Later phases will extend this grammar with {@code if}, {@code for}, {@code while},
 * {@code case}, function definitions, and more.
 */
public class Parser {

    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses the entire token stream and returns the root AST node.
     *
     * @throws ParseException if the token stream is syntactically invalid.
     */
    public Node parse() {
        skipNewlines();
        if (peek().isEof()) {
            return null; // empty input
        }
        Node result = parseList();
        skipNewlines();
        if (!peek().isEof()) {
            Token t = peek();
            throw new ParseException("Unexpected token: " + t.value(), t.line(), t.column());
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Grammar rules
    // -----------------------------------------------------------------------

    /**
     * list := and_or ( (';' | '&' | NEWLINE) and_or )* (';' | '&' | NEWLINE)?
     */
    private Node parseList() {
        List<Node> commands    = new ArrayList<>();
        List<String> separators = new ArrayList<>();

        commands.add(parseAndOr());

        while (true) {
            Token t = peek();
            if (t.type() == TokenType.SEMI || t.type() == TokenType.AMP
                    || t.type() == TokenType.NEWLINE) {
                String sep = t.value();
                advance();
                skipNewlines();
                separators.add(sep);

                // If there's another command following the separator, parse it
                if (!isListTerminator(peek())) {
                    commands.add(parseAndOr());
                } else {
                    break;
                }
            } else {
                separators.add(";"); // implicit sequential
                break;
            }
        }

        if (commands.size() == 1 && separators.size() == 1 && separators.get(0).equals(";")) {
            // Unwrap trivial single-command lists
            return commands.get(0);
        }
        return new CommandList(commands, separators);
    }

    /**
     * and_or := pipeline ( ('&&' | '||') pipeline )*
     */
    private Node parseAndOr() {
        Node left = parsePipeline();

        while (true) {
            Token t = peek();
            if (t.type() == TokenType.AND || t.type() == TokenType.OR) {
                String op = t.value();
                advance();
                skipNewlines();
                Node right = parsePipeline();
                left = new AndOr(left, op, right);
            } else {
                break;
            }
        }
        return left;
    }

    /**
     * pipeline := '!'? command ( '|' command )*
     */
    private Node parsePipeline() {
        boolean negate = false;
        if (peek().type() == TokenType.BANG) {
            negate = true;
            advance();
        }

        List<Node> stages = new ArrayList<>();
        stages.add(parseCommand());

        while (peek().type() == TokenType.PIPE) {
            advance();
            skipNewlines();
            stages.add(parseCommand());
        }

        if (!negate && stages.size() == 1) {
            return stages.get(0); // unwrap trivial single-stage pipeline
        }
        return new Pipeline(negate, stages);
    }

    /**
     * command := simple_command | subshell | group_command
     */
    private Node parseCommand() {
        Token t = peek();
        if (t.type() == TokenType.LPAREN) {
            return parseSubshell();
        }
        if (t.type() == TokenType.LBRACE) {
            return parseGroupCommand();
        }
        return parseSimpleCommand();
    }

    /**
     * subshell := '(' list ')' redirect*
     */
    private Node parseSubshell() {
        consume(TokenType.LPAREN);
        skipNewlines();
        Node body = parseList();
        skipNewlines();
        consume(TokenType.RPAREN);
        List<Redirection> redirects = parseRedirections();
        return new Subshell(body, redirects);
    }

    /**
     * group_command := '{' list '}' redirect*
     */
    private Node parseGroupCommand() {
        consume(TokenType.LBRACE);
        skipNewlines();
        Node body = parseList();
        skipNewlines();
        consume(TokenType.RBRACE);
        List<Redirection> redirects = parseRedirections();
        return new GroupCommand(body, redirects);
    }

    /**
     * simple_command := (ASSIGNMENT_WORD)* WORD* redirect*
     */
    private Node parseSimpleCommand() {
        List<Assignment>  assignments = new ArrayList<>();
        List<Word>        words       = new ArrayList<>();
        List<Redirection> redirects   = new ArrayList<>();

        // Leading assignment words (only before the first non-assignment WORD)
        while (peek().type() == TokenType.ASSIGNMENT_WORD) {
            String raw   = peek().value();
            int eq       = raw.indexOf('=');
            String name  = raw.substring(0, eq);
            String value = raw.substring(eq + 1);
            assignments.add(new Assignment(name, value));
            advance();
        }

        // Parse any redirections before or without words
        redirects.addAll(parseRedirections());

        // Command name and arguments
        while (peek().type() == TokenType.WORD || peek().type() == TokenType.ASSIGNMENT_WORD) {
            words.add(Word.fromToken(peek()));
            advance();

            // After each WORD we may encounter redirects interspersed with more words
            redirects.addAll(parseRedirections());
        }

        if (assignments.isEmpty() && words.isEmpty() && redirects.isEmpty()) {
            Token t = peek();
            throw new ParseException("Unexpected token: " + (t.isEof() ? "EOF" : t.value()), t.line(), t.column());
        }

        return SimpleCommand.of(assignments, words, redirects);
    }

    /**
     * redirect := REDIRECT_* WORD
     *
     * <p>Parses zero or more redirections interleaved at the current position.
     */
    private List<Redirection> parseRedirections() {
        List<Redirection> list = new ArrayList<>();
        while (isRedirectToken(peek())) {
            Token op = advance();
            TokenType type = op.type();

            // FD duplication operators don't need a separate target WORD
            if (type == TokenType.REDIRECT_ERR_TO_OUT || type == TokenType.REDIRECT_OUT_TO_ERR) {
                list.add(new Redirection(type, -1, op.value()));
                continue;
            }

            // Expect a target word
            if (peek().type() != TokenType.WORD && peek().type() != TokenType.ASSIGNMENT_WORD) {
                Token bad = peek();
                throw new ParseException("Expected redirect target, got: " + bad.value(),
                        bad.line(), bad.column());
            }
            String target = advance().value();

            // Parse explicit FD from compound tokens like "2>" or "3<"
            int fd = -1;
            String opVal = op.value();
            if ((type == TokenType.REDIRECT_ERR || type == TokenType.REDIRECT_ERR_APPEND)
                    && opVal.startsWith("2")) {
                fd = 2;
            } else if (type == TokenType.REDIRECT_FD_OUT || type == TokenType.REDIRECT_FD_IN) {
                String digits = opVal.replaceAll("[><&].*", "");
                try { fd = Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
            }

            list.add(new Redirection(type, fd, target));
        }
        return list;
    }

    // -----------------------------------------------------------------------
    // Token navigation helpers
    // -----------------------------------------------------------------------

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        Token t = tokens.get(pos);
        if (t.type() != TokenType.EOF) pos++;
        return t;
    }

    private Token consume(TokenType expected) {
        Token t = peek();
        if (t.type() != expected) {
            throw new ParseException(
                    "Expected " + expected + " but got " + t.type() + " (\"" + t.value() + "\")",
                    t.line(), t.column());
        }
        return advance();
    }

    private void skipNewlines() {
        while (peek().type() == TokenType.NEWLINE) advance();
    }

    private boolean isListTerminator(Token t) {
        return t.isEof()
                || t.type() == TokenType.RPAREN
                || t.type() == TokenType.RBRACE;
    }

    private boolean isRedirectToken(Token t) {
        return switch (t.type()) {
            case REDIRECT_OUT, REDIRECT_APPEND, REDIRECT_IN,
                 HERE_DOC, HERE_STRING,
                 REDIRECT_ERR, REDIRECT_ERR_APPEND,
                 REDIRECT_ALL, REDIRECT_ALL_APPEND,
                 REDIRECT_ERR_TO_OUT, REDIRECT_OUT_TO_ERR,
                 REDIRECT_FD_OUT, REDIRECT_FD_IN -> true;
            default -> false;
        };
    }
}
