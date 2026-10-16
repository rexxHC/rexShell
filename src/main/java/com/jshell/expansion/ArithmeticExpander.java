package com.jshell.expansion;

import com.jshell.environment.Environment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates bash-style arithmetic expansions: {@code $((expression))}.
 *
 * <p>Supports:
 * <ul>
 *   <li>64-bit integer arithmetic (long)</li>
 *   <li>Grouping: {@code ()}</li>
 *   <li>Unary: {@code +}, {@code -}, {@code ~}, {@code !}</li>
 *   <li>Exponentiation: {@code **}</li>
 *   <li>Multiplicative: {@code *}, {@code /}, {@code %}</li>
 *   <li>Additive: {@code +}, {@code -}</li>
 *   <li>Bitwise shifts: {@code <<}, {@code >>}</li>
 *   <li>Relational &amp; Equality: {@code <}, {@code <=}, {@code >}, {@code >=}, {@code ==}, {@code !=}</li>
 *   <li>Bitwise: {@code &}, {@code ^}, {@code |}</li>
 *   <li>Logical: {@code &&}, {@code ||}</li>
 *   <li>Conditional ternary: {@code ? :}</li>
 *   <li>Assignments: {@code =}, {@code +=}, {@code -=}, {@code *=}, {@code /=}, etc.</li>
 *   <li>Variable lookups: {@code $((x + 5))} or {@code $(($x + 5))}</li>
 * </ul>
 */
public class ArithmeticExpander {

    private static final Pattern ARITHMETIC_PATTERN = Pattern.compile("\\$\\(\\((.*?)\\)\\)");

    /**
     * Expands all {@code $((...))} occurrences in the input string.
     */
    public String expand(String text, Environment env) {
        if (text == null || !text.contains("$((")) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            if (text.startsWith("$((", i)) {
                // Find matching "))" taking into account nested parentheses
                int depth = 1;
                int j = i + 3;
                while (j < len && depth > 0) {
                    if (text.startsWith("((", j)) {
                        depth++;
                        j += 2;
                    } else if (text.startsWith("))", j)) {
                        depth--;
                        if (depth == 0) break;
                        j += 2;
                    } else {
                        j++;
                    }
                }

                if (depth == 0) {
                    result.append(text, lastEnd, i);
                    String expr = text.substring(i + 3, j);
                    long val = evaluate(expr, env);
                    result.append(val);
                    i = j + 1; // skip "))"
                    lastEnd = i + 1;
                }
            }
        }

        result.append(text.substring(lastEnd));
        return result.toString();
    }

    /**
     * Evaluates a mathematical expression string using a recursive descent parser.
     */
    public long evaluate(String expression, Environment env) {
        Parser p = new Parser(expression.trim(), env);
        return p.parse();
    }

    // -----------------------------------------------------------------------
    // Recursive descent expression parser
    // -----------------------------------------------------------------------

    private static class Parser {
        private final String input;
        private final Environment env;
        private int pos = 0;

        Parser(String input, Environment env) {
            this.input = input;
            this.env = env;
        }

        long parse() {
            skipWhitespace();
            if (pos >= input.length()) return 0;
            long val = parseAssignment();
            skipWhitespace();
            return val;
        }

        // Assignment: var = expr, var += expr, etc.
        private long parseAssignment() {
            int savedPos = pos;
            String ident = readIdentifier();
            if (ident != null) {
                skipWhitespace();
                String op = readAssignmentOp();
                if (op != null) {
                    long rhs = parseAssignment();
                    long current = getVariableValue(ident);
                    long newVal = switch (op) {
                        case "=" -> rhs;
                        case "+=" -> current + rhs;
                        case "-=" -> current - rhs;
                        case "*=" -> current * rhs;
                        case "/=" -> (rhs == 0) ? 0 : current / rhs;
                        case "%=" -> (rhs == 0) ? 0 : current % rhs;
                        case "<<=" -> current << rhs;
                        case ">>=" -> current >> rhs;
                        case "&=" -> current & rhs;
                        case "^=" -> current ^ rhs;
                        case "|=" -> current | rhs;
                        default -> rhs;
                    };
                    env.set(ident, String.valueOf(newVal));
                    return newVal;
                }
            }
            // Backtrack if not assignment
            pos = savedPos;
            return parseTernary();
        }

        // Ternary: cond ? then : else
        private long parseTernary() {
            long cond = parseLogicalOr();
            skipWhitespace();
            if (match("?")) {
                long thenVal = parseAssignment();
                skipWhitespace();
                consume(":");
                long elseVal = parseAssignment();
                return (cond != 0) ? thenVal : elseVal;
            }
            return cond;
        }

        // Logical OR: ||
        private long parseLogicalOr() {
            long left = parseLogicalAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    long right = parseLogicalAnd();
                    left = (left != 0 || right != 0) ? 1 : 0;
                } else {
                    break;
                }
            }
            return left;
        }

        // Logical AND: &&
        private long parseLogicalAnd() {
            long left = parseBitwiseOr();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    long right = parseBitwiseOr();
                    left = (left != 0 && right != 0) ? 1 : 0;
                } else {
                    break;
                }
            }
            return left;
        }

        // Bitwise OR: |
        private long parseBitwiseOr() {
            long left = parseBitwiseXor();
            while (true) {
                skipWhitespace();
                if (peek() == '|' && peekAhead(1) != '|') {
                    advance();
                    long right = parseBitwiseXor();
                    left = left | right;
                } else {
                    break;
                }
            }
            return left;
        }

        // Bitwise XOR: ^
        private long parseBitwiseXor() {
            long left = parseBitwiseAnd();
            while (true) {
                skipWhitespace();
                if (match("^")) {
                    long right = parseBitwiseAnd();
                    left = left ^ right;
                } else {
                    break;
                }
            }
            return left;
        }

        // Bitwise AND: &
        private long parseBitwiseAnd() {
            long left = parseEquality();
            while (true) {
                skipWhitespace();
                if (peek() == '&' && peekAhead(1) != '&') {
                    advance();
                    long right = parseEquality();
                    left = left & right;
                } else {
                    break;
                }
            }
            return left;
        }

        // Equality: ==, !=
        private long parseEquality() {
            long left = parseRelational();
            while (true) {
                skipWhitespace();
                if (match("==")) {
                    long right = parseRelational();
                    left = (left == right) ? 1 : 0;
                } else if (match("!=")) {
                    long right = parseRelational();
                    left = (left != right) ? 1 : 0;
                } else {
                    break;
                }
            }
            return left;
        }

        // Relational: <, <=, >, >=
        private long parseRelational() {
            long left = parseShift();
            while (true) {
                skipWhitespace();
                if (match("<=")) {
                    long right = parseShift();
                    left = (left <= right) ? 1 : 0;
                } else if (match(">=")) {
                    long right = parseShift();
                    left = (left >= right) ? 1 : 0;
                } else if (peek() == '<' && peekAhead(1) != '<') {
                    advance();
                    long right = parseShift();
                    left = (left < right) ? 1 : 0;
                } else if (peek() == '>' && peekAhead(1) != '>') {
                    advance();
                    long right = parseShift();
                    left = (left > right) ? 1 : 0;
                } else {
                    break;
                }
            }
            return left;
        }

        // Bitwise shifts: <<, >>
        private long parseShift() {
            long left = parseAdditive();
            while (true) {
                skipWhitespace();
                if (match("<<")) {
                    long right = parseAdditive();
                    left = left << right;
                } else if (match(">>")) {
                    long right = parseAdditive();
                    left = left >> right;
                } else {
                    break;
                }
            }
            return left;
        }

        // Additive: +, -
        private long parseAdditive() {
            long left = parseMultiplicative();
            while (true) {
                skipWhitespace();
                if (peek() == '+' && peekAhead(1) != '=' && peekAhead(1) != '+') {
                    advance();
                    long right = parseMultiplicative();
                    left = left + right;
                } else if (peek() == '-' && peekAhead(1) != '=' && peekAhead(1) != '-') {
                    advance();
                    long right = parseMultiplicative();
                    left = left - right;
                } else {
                    break;
                }
            }
            return left;
        }

        // Multiplicative: *, /, %
        private long parseMultiplicative() {
            long left = parseExponent();
            while (true) {
                skipWhitespace();
                if (match("*") && peek() != '*') { // note: exponent is handled in parseExponent
                    long right = parseExponent();
                    left = left * right;
                } else if (match("/")) {
                    long right = parseExponent();
                    left = (right == 0) ? 0 : left / right;
                } else if (match("%")) {
                    long right = parseExponent();
                    left = (right == 0) ? 0 : left % right;
                } else {
                    break;
                }
            }
            return left;
        }

        // Exponent: ** (right-associative)
        private long parseExponent() {
            long left = parseUnary();
            skipWhitespace();
            if (match("**")) {
                long right = parseExponent();
                return (long) Math.pow(left, right);
            }
            return left;
        }

        // Unary: +, -, ~, !
        private long parseUnary() {
            skipWhitespace();
            if (match("+")) {
                return parseUnary();
            }
            if (match("-")) {
                return -parseUnary();
            }
            if (match("~")) {
                return ~parseUnary();
            }
            if (match("!")) {
                return (parseUnary() == 0) ? 1 : 0;
            }
            return parsePrimary();
        }

        // Primary: number, variable, (expr)
        private long parsePrimary() {
            skipWhitespace();
            if (match("(")) {
                long val = parseAssignment();
                skipWhitespace();
                consume(")");
                return val;
            }

            // Variable with leading '$'
            if (match("$")) {
                String name = readIdentifier();
                if (name != null) {
                    return getVariableValue(name);
                }
                return 0;
            }

            // Number (decimal, octal 0..., hex 0x...)
            if (Character.isDigit(peek())) {
                return readNumber();
            }

            // Variable identifier without '$'
            String ident = readIdentifier();
            if (ident != null) {
                return getVariableValue(ident);
            }

            return 0;
        }

        private long getVariableValue(String name) {
            String val = env.get(name);
            if (val == null || val.isBlank()) return 0;
            try {
                // If the variable holds an arithmetic expression, evaluate it
                return new ArithmeticExpander().evaluate(val.trim(), env);
            } catch (Exception e) {
                return 0;
            }
        }

        private long readNumber() {
            int start = pos;
            if (peek() == '0' && (peekAhead(1) == 'x' || peekAhead(1) == 'X')) {
                pos += 2;
                while (pos < input.length() && isHex(peek())) pos++;
                String hex = input.substring(start + 2, pos);
                return Long.parseLong(hex, 16);
            }
            if (peek() == '0' && pos + 1 < input.length() && Character.isDigit(peekAhead(1))) {
                pos++;
                while (pos < input.length() && peek() >= '0' && peek() <= '7') pos++;
                String octal = input.substring(start, pos);
                return Long.parseLong(octal, 8);
            }
            while (pos < input.length() && Character.isDigit(peek())) pos++;
            String dec = input.substring(start, pos);
            return Long.parseLong(dec);
        }

        private String readIdentifier() {
            int start = pos;
            if (pos < input.length() && (Character.isLetter(peek()) || peek() == '_')) {
                pos++;
                while (pos < input.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                    pos++;
                }
                return input.substring(start, pos);
            }
            return null;
        }

        private String readAssignmentOp() {
            String[] ops = {"<<=", ">>=", "+=", "-=", "*=", "/=", "%=", "&=", "^=", "|=", "="};
            for (String op : ops) {
                if (match(op)) return op;
            }
            return null;
        }

        private boolean match(String s) {
            if (input.startsWith(s, pos)) {
                pos += s.length();
                return true;
            }
            return false;
        }

        private void consume(String s) {
            if (!match(s)) {
                // Ignore mismatch gracefully
            }
        }

        private char peek() {
            return (pos < input.length()) ? input.charAt(pos) : '\0';
        }

        private char peekAhead(int offset) {
            int p = pos + offset;
            return (p < input.length()) ? input.charAt(p) : '\0';
        }

        private void advance() {
            if (pos < input.length()) pos++;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean isHex(char c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }
    }
}
