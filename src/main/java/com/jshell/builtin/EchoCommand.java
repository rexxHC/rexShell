package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Built-in {@code echo} command.
 *
 * <p>Usage: {@code echo [-n] [-e] [arg ...]}
 * <ul>
 *   <li>{@code -n} — do not output the trailing newline</li>
 *   <li>{@code -e} — enable interpretation of backslash escapes
 *       ({@code \n}, {@code \t}, {@code \r}, {@code \\}, {@code \a}, {@code \b},
 *       {@code \0NNN} octal, {@code \xHH} hex, {@code \c} suppress further output)</li>
 *   <li>{@code -E} — disable escape interpretation (default)</li>
 * </ul>
 */
public class EchoCommand implements Builtin {

    @Override
    public String name() { return "echo"; }

    @Override
    public String usage() { return "echo [-n] [-e] [-E] [arg ...]"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        boolean newline      = true;
        boolean escapes      = false;
        int argStart         = 1;

        // Parse flags (only leading flags before the first non-flag argument)
        outer:
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-") && a.length() > 1) {
                for (char c : a.substring(1).toCharArray()) {
                    switch (c) {
                        case 'n' -> newline = false;
                        case 'e' -> escapes = true;
                        case 'E' -> escapes = false;
                        default  -> { break outer; } // Unknown flag: treat as argument
                    }
                }
                argStart = i + 1;
            } else {
                break;
            }
        }

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();

        for (int i = argStart; i < args.length; i++) {
            if (i > argStart) sb.append(' ');
            sb.append(args[i]);
        }

        String output = escapes ? processEscapes(sb.toString()) : sb.toString();
        out.print(output);
        if (newline) out.print("\n");
        out.flush();

        return 0;
    }

    /**
     * Processes {@code \}-escape sequences in the output string.
     */
    private String processEscapes(String s) {
        StringBuilder result = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n'  -> { result.append('\n'); i++; }
                    case 't'  -> { result.append('\t'); i++; }
                    case 'r'  -> { result.append('\r'); i++; }
                    case '\\'  -> { result.append('\\'); i++; }
                    case 'a'  -> { result.append('\u0007'); i++; }
                    case 'b'  -> { result.append('\b'); i++; }
                    case 'e'  -> { result.append('\u001B'); i++; }
                    case 'f'  -> { result.append('\f'); i++; }
                    case 'v'  -> { result.append('\u000B'); i++; }
                    case 'c'  -> { return result.toString(); } // \c suppresses rest of output
                    case '0'  -> {
                        // Octal \0NNN
                        int end = Math.min(i + 5, s.length());
                        int j = i + 2;
                        while (j < end && isOctal(s.charAt(j))) j++;
                        if (j > i + 2) {
                            int code = Integer.parseInt(s.substring(i + 2, j), 8);
                            result.append((char) code);
                            i = j - 1;
                        } else {
                            result.append('\0'); i++;
                        }
                    }
                    case 'x'  -> {
                        // Hex \xHH
                        int end = Math.min(i + 4, s.length());
                        int j = i + 2;
                        while (j < end && isHex(s.charAt(j))) j++;
                        if (j > i + 2) {
                            int code = Integer.parseInt(s.substring(i + 2, j), 16);
                            result.append((char) code);
                            i = j - 1;
                        } else {
                            result.append(c);
                        }
                    }
                    default -> result.append(c); // Unknown escape: keep as-is
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private boolean isOctal(char c) { return c >= '0' && c <= '7'; }
    private boolean isHex(char c)   { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
}
