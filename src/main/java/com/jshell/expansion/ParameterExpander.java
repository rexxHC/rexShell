package com.jshell.expansion;

import com.jshell.environment.Environment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all bash parameter and variable expansions:
 * <ul>
 *   <li>Simple variables: {@code $VAR}, {@code ${VAR}}</li>
 *   <li>Special parameters: {@code $?}, {@code $$}, {@code $!}, {@code $0}, {@code $#}, {@code $@}, {@code $*}</li>
 *   <li>Default value: {@code ${VAR:-default}}, {@code ${VAR-default}}</li>
 *   <li>Assign default: {@code ${VAR:=default}}, {@code ${VAR=default}}</li>
 *   <li>Alternate value: {@code ${VAR:+alt}}, {@code ${VAR+alt}}</li>
 *   <li>Error on unset: {@code ${VAR:?err}}, {@code ${VAR?err}}</li>
 *   <li>String length: {@code ${#VAR}}</li>
 *   <li>Leading pattern removal: {@code ${VAR#pattern}} (shortest), {@code ${VAR##pattern}} (longest)</li>
 *   <li>Trailing pattern removal: {@code ${VAR%pattern}} (shortest), {@code ${VAR%%pattern}} (longest)</li>
 *   <li>Pattern substitution: {@code ${VAR/pattern/replacement}} (first), {@code ${VAR//pattern/replacement}} (all)</li>
 *   <li>Substring slicing: {@code ${VAR:offset}}, {@code ${VAR:offset:length}}</li>
 * </ul>
 */
public class ParameterExpander {

    public String expand(String text, Environment env) {
        if (text == null || !text.contains("$")) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Escaped dollar: \$ -> literal $
            if (c == '\\' && i + 1 < len && text.charAt(i + 1) == '$') {
                result.append('$');
                i++;
                continue;
            }

            if (c == '$' && i + 1 < len) {
                char next = text.charAt(i + 1);

                // Arithmetic $((...)) — skip, handled by ArithmeticExpander
                if (next == '(' && i + 2 < len && text.charAt(i + 2) == '(') {
                    result.append('$');
                    continue;
                }

                // Command substitution $(...) — skip, handled by CommandSubstitution
                if (next == '(') {
                    result.append('$');
                    continue;
                }

                // Braced parameter expansion: ${...}
                if (next == '{') {
                    int close = findClosingBrace(text, i + 2);
                    if (close > 0) {
                        String body = text.substring(i + 2, close);
                        String val = expandBraced(body, env);
                        result.append(val);
                        i = close;
                        continue;
                    }
                }

                // Special single-character parameters: $?, $$, $!, $0, $#, $@, $*
                if (isSpecialParam(next)) {
                    result.append(getSpecialParam(next, env));
                    i++;
                    continue;
                }

                // Simple variable name: $VAR
                if (Character.isLetter(next) || next == '_') {
                    int start = i + 1;
                    int j = start;
                    while (j < len && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                        j++;
                    }
                    String varName = text.substring(start, j);
                    String val = env.get(varName);
                    if (val != null) {
                        result.append(val);
                    }
                    i = j - 1;
                    continue;
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    private int findClosingBrace(String text, int start) {
        int depth = 1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private boolean isSpecialParam(char c) {
        return switch (c) {
            case '?', '$', '!', '0', '#', '@', '*' -> true;
            default -> false;
        };
    }

    private String getSpecialParam(char c, Environment env) {
        return switch (c) {
            case '?' -> String.valueOf(env.getLastExitStatus());
            case '$' -> env.get("$") != null ? env.get("$") : String.valueOf(ProcessHandle.current().pid());
            case '!' -> env.get("!") != null ? env.get("!") : "0";
            case '0' -> env.get("0") != null ? env.get("0") : "jshell";
            case '#' -> env.get("#") != null ? env.get("#") : "0";
            case '@', '*' -> env.get("*") != null ? env.get("*") : "";
            default -> "";
        };
    }

    /**
     * Expands content inside {@code ${...}}.
     */
    public String expandBraced(String body, Environment env) {
        if (body.isEmpty()) return "";

        // Length: ${#VAR}
        if (body.startsWith("#") && body.length() > 1 && !body.startsWith("#!")) {
            String varName = body.substring(1);
            String val = env.get(varName);
            return String.valueOf(val != null ? val.length() : 0);
        }

        // Special single-character parameters: ${?}, ${$}, etc.
        if (body.length() == 1 && isSpecialParam(body.charAt(0))) {
            return getSpecialParam(body.charAt(0), env);
        }

        // Locate the variable identifier boundary
        int identEnd = 0;
        if (isSpecialParam(body.charAt(0))) {
            identEnd = 1;
        } else {
            while (identEnd < body.length()) {
                char c = body.charAt(identEnd);
                if (identEnd == 0) {
                    if (!Character.isLetter(c) && c != '_') break;
                } else {
                    if (!Character.isLetterOrDigit(c) && c != '_') break;
                }
                identEnd++;
            }
        }

        // Pure variable reference: ${VAR}
        if (identEnd >= body.length()) {
            String val = env.get(body);
            return (val != null) ? val : "";
        }

        String varName = body.substring(0, identEnd);
        String opAndRest = body.substring(identEnd);

        // 1. Parameter expansion conditionals: :-, -, :=, =, :+, +, :?, ?
        String[] condOps = {":-", "-", ":=", "=", ":+", "+", ":?", "?"};
        for (String op : condOps) {
            if (opAndRest.startsWith(op)) {
                String word = opAndRest.substring(op.length());
                return expandConditional(varName, op, word, env);
            }
        }

        // 2. Substring slicing: :offset or :offset:length
        if (opAndRest.startsWith(":")) {
            return expandSubstring(varName, opAndRest.substring(1), env);
        }

        // 3. Pattern substitution: //pat/rep or /pat/rep
        if (opAndRest.startsWith("//")) {
            return expandPatternSubstitution(varName, opAndRest.substring(1), env); // passes "/pat/rep" for all=true
        }
        if (opAndRest.startsWith("/")) {
            return expandPatternSubstitution(varName, opAndRest.substring(1), env); // passes "pat/rep" for all=false
        }

        // 4. Pattern removal: ##, #, %%, %
        if (opAndRest.startsWith("##")) {
            return removePrefix(env.get(varName), opAndRest.substring(2), true);
        }
        if (opAndRest.startsWith("#")) {
            return removePrefix(env.get(varName), opAndRest.substring(1), false);
        }
        if (opAndRest.startsWith("%%")) {
            return removeSuffix(env.get(varName), opAndRest.substring(2), true);
        }
        if (opAndRest.startsWith("%")) {
            return removeSuffix(env.get(varName), opAndRest.substring(1), false);
        }

        // Fallback
        String val = env.get(varName);
        return (val != null) ? val : "";
    }

    private String expandConditional(String varName, String op, String word, Environment env) {
        String val = env.get(varName);
        boolean isSet = env.isSet(varName);
        boolean isUnsetOrEmpty = (val == null || val.isEmpty());
        boolean condition = op.startsWith(":") ? isUnsetOrEmpty : !isSet;

        return switch (op) {
            case ":-", "-" -> {
                if (condition) {
                    yield expand(word, env);
                }
                yield (val != null) ? val : "";
            }
            case ":=", "=" -> {
                if (condition) {
                    String expandedWord = expand(word, env);
                    env.set(varName, expandedWord);
                    yield expandedWord;
                }
                yield (val != null) ? val : "";
            }
            case ":+", "+" -> {
                if (!condition) {
                    yield expand(word, env);
                }
                yield "";
            }
            case ":?", "?" -> {
                if (condition) {
                    String msg = word.isEmpty() ? "parameter null or not set" : expand(word, env);
                    throw new RuntimeException("jshell: " + varName + ": " + msg);
                }
                yield (val != null) ? val : "";
            }
            default -> (val != null) ? val : "";
        };
    }

    private String expandSubstring(String varName, String sliceSpec, Environment env) {
        String val = env.get(varName);
        if (val == null) return "";

        int secondColon = sliceSpec.indexOf(':');
        int offset;
        int length = -1;

        try {
            if (secondColon >= 0) {
                offset = Integer.parseInt(sliceSpec.substring(0, secondColon).trim());
                length = Integer.parseInt(sliceSpec.substring(secondColon + 1).trim());
            } else {
                offset = Integer.parseInt(sliceSpec.trim());
            }
        } catch (NumberFormatException e) {
            return val;
        }

        int valLen = val.length();
        if (offset < 0) {
            offset = valLen + offset;
        }
        if (offset < 0) offset = 0;
        if (offset >= valLen) return "";

        if (length < 0) {
            return val.substring(offset);
        }
        int end = Math.min(offset + length, valLen);
        return val.substring(offset, end);
    }

    private String expandPatternSubstitution(String varName, String rest, Environment env) {
        String val = env.get(varName);
        if (val == null) return "";

        boolean all = false;
        if (rest.startsWith("/")) {
            all = true;
            rest = rest.substring(1);
        }

        int slash = rest.indexOf('/');
        String patStr = (slash >= 0) ? rest.substring(0, slash) : rest;
        String repStr = (slash >= 0) ? rest.substring(slash + 1) : "";

        String regex = globToRegex(patStr);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(val);

        if (all) {
            return matcher.replaceAll(Matcher.quoteReplacement(repStr));
        } else {
            return matcher.replaceFirst(Matcher.quoteReplacement(repStr));
        }
    }

    private String removePrefix(String val, String glob, boolean longest) {
        if (val == null) return "";
        String regex = globToRegex(glob);

        if (longest) {
            for (int len = val.length(); len >= 0; len--) {
                String sub = val.substring(0, len);
                if (sub.matches(regex)) {
                    return val.substring(len);
                }
            }
        } else {
            for (int len = 0; len <= val.length(); len++) {
                String sub = val.substring(0, len);
                if (sub.matches(regex)) {
                    return val.substring(len);
                }
            }
        }
        return val;
    }

    private String removeSuffix(String val, String glob, boolean longest) {
        if (val == null) return "";
        String regex = globToRegex(glob);

        if (longest) {
            for (int start = 0; start <= val.length(); start++) {
                String sub = val.substring(start);
                if (sub.matches(regex)) {
                    return val.substring(0, start);
                }
            }
        } else {
            for (int start = val.length(); start >= 0; start--) {
                String sub = val.substring(start);
                if (sub.matches(regex)) {
                    return val.substring(0, start);
                }
            }
        }
        return val;
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        int len = glob.length();
        for (int i = 0; i < len; i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '[' -> {
                    int close = glob.indexOf(']', i + 1);
                    if (close > 0) {
                        String bracket = glob.substring(i, close + 1);
                        if (bracket.startsWith("[!")) {
                            regex.append("[^").append(bracket.substring(2));
                        } else {
                            regex.append(bracket);
                        }
                        i = close;
                    } else {
                        regex.append("\\[");
                    }
                }
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '\\' -> {
                    regex.append('\\').append(c);
                }
                default -> regex.append(c);
            }
        }
        return regex.toString();
    }
}
