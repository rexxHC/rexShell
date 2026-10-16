package com.jshell.expansion;

import com.jshell.environment.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs field splitting (word splitting) on unquoted expansion results
 * using the characters in the {@code $IFS} environment variable.
 *
 * <p>Standard rules:
 * <ul>
 *   <li>If {@code $IFS} is unset, default is {@code " \t\n"}.</li>
 *   <li>If {@code $IFS} is empty, no word splitting is performed.</li>
 *   <li>IFS whitespace characters (space, tab, newline) at the beginning or end of
 *       the input are discarded, and sequences of IFS whitespace delimit fields.</li>
 *   <li>Non-whitespace IFS characters act as strict delimiters.</li>
 * </ul>
 */
public class WordSplitter {

    private static final String DEFAULT_IFS = " \t\n";

    /**
     * Splits an unquoted expanded string into words according to {@code $IFS}.
     *
     * @param text The string to split.
     * @param env  The shell environment.
     * @return List of split words.
     */
    public List<String> split(String text, Environment env) {
        if (text == null) return List.of();
        if (text.isEmpty()) return List.of("");

        String ifs = env.isSet("IFS") ? env.get("IFS") : DEFAULT_IFS;
        if (ifs == null || ifs.isEmpty()) {
            // Empty IFS disables word splitting
            return List.of(text);
        }

        List<String> result = new ArrayList<>();
        int len = text.length();
        int pos = 0;

        // Skip leading IFS whitespace
        while (pos < len && isIfsWhitespace(text.charAt(pos), ifs)) {
            pos++;
        }

        StringBuilder current = new StringBuilder();

        while (pos < len) {
            char c = text.charAt(pos);

            if (ifs.indexOf(c) >= 0) {
                // Delimiter encountered
                result.add(current.toString());
                current.setLength(0);

                if (isIfsWhitespace(c, ifs)) {
                    // Skip remaining consecutive IFS whitespace
                    while (pos + 1 < len && isIfsWhitespace(text.charAt(pos + 1), ifs)) {
                        pos++;
                    }
                }
            } else {
                current.append(c);
            }
            pos++;
        }

        // Add final word if present or if delimited by non-whitespace
        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private boolean isIfsWhitespace(char c, String ifs) {
        return (c == ' ' || c == '\t' || c == '\n' || c == '\r') && ifs.indexOf(c) >= 0;
    }
}
