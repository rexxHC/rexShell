package com.jshell.expansion;

import com.jshell.environment.Environment;
import com.jshell.platform.Platform;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles pathname expansion (globbing): {@code *}, {@code ?}, {@code [...]}, {@code **}.
 *
 * <p>Expands filename patterns relative to {@link Environment#getWorkingDirectory()}.
 * If no matching files or directories exist, the original pattern string is preserved
 * (default bash behavior without {@code nullglob}).
 */
public class GlobExpander {

    private final Platform platform;

    public GlobExpander(Platform platform) {
        this.platform = platform;
    }

    /**
     * Expands a word that may contain glob characters.
     *
     * @param pattern The pattern word to expand.
     * @param env     The shell environment.
     * @return List of matching file/directory paths (sorted), or original pattern if no match.
     */
    public List<String> expand(String pattern, Environment env) {
        if (!hasGlobMeta(pattern)) {
            return List.of(pattern);
        }

        Path cwd = env.getWorkingDirectory();
        List<String> matches = new ArrayList<>();

        try {
            // Determine base directory and relative pattern
            String normalized = pattern.replace('\\', '/');
            int lastSlash = normalized.lastIndexOf('/');

            Path searchDir;
            String filePattern;
            String prefix;

            if (lastSlash >= 0) {
                String dirPart = normalized.substring(0, lastSlash);
                filePattern = normalized.substring(lastSlash + 1);

                if (dirPart.isEmpty()) {
                    searchDir = Paths.get("/");
                    prefix = "/";
                } else {
                    Path p = Paths.get(dirPart);
                    searchDir = p.isAbsolute() ? p : cwd.resolve(p).normalize();
                    prefix = dirPart + "/";
                }
            } else {
                searchDir = cwd;
                filePattern = normalized;
                prefix = "";
            }

            if (!Files.exists(searchDir) || !Files.isDirectory(searchDir)) {
                return List.of(pattern);
            }

            // Convert shell glob to regex
            String regex = globToRegex(filePattern);
            java.util.regex.Pattern reg = java.util.regex.Pattern.compile(regex);

            boolean matchHidden = filePattern.startsWith(".");

            try (Stream<Path> stream = Files.list(searchDir)) {
                stream.forEach(entry -> {
                    String fileName = entry.getFileName().toString();
                    if (!matchHidden && fileName.startsWith(".")) {
                        return; // do not match hidden files by default
                    }
                    if (reg.matcher(fileName).matches()) {
                        matches.add(prefix + fileName);
                    }
                });
            }

        } catch (Exception ignored) {
            // Fall back to original pattern on filesystem error
            return List.of(pattern);
        }

        if (matches.isEmpty()) {
            return List.of(pattern);
        }

        Collections.sort(matches);
        return matches;
    }

    /**
     * Returns true if the string contains any unescaped glob metacharacters (*, ?, [).
     */
    public static boolean hasGlobMeta(String s) {
        if (s == null) return false;
        boolean inClass = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '[') inClass = true;
            if (c == ']' && inClass) inClass = false;
            if (c == '*' || c == '?' || (c == '[' && !inClass)) return true;
        }
        return false;
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        int len = glob.length();

        for (int i = 0; i < len; i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    // Check for ** (globstar)
                    if (i + 1 < len && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
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
                case '.', '(', ')', '+', '|', '^', '$', '{', '}', '\\' -> {
                    regex.append('\\').append(c);
                }
                default -> regex.append(c);
            }
        }

        regex.append("$");
        return regex.toString();
    }
}
