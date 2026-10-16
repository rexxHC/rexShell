package com.jshell.expansion;

import com.jshell.environment.Environment;
import com.jshell.lexer.WordPart;
import com.jshell.parser.ast.Word;
import com.jshell.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Orchestrates all shell word expansions in standard bash order:
 * <ol>
 *   <li>Tilde expansion</li>
 *   <li>Parameter and variable expansion</li>
 *   <li>Command substitution</li>
 *   <li>Arithmetic expansion</li>
 *   <li>Word splitting (IFS)</li>
 *   <li>Pathname expansion (globbing)</li>
 * </ol>
 */
public class Expander {

    private final TildeExpander       tildeExpander;
    private final ParameterExpander   parameterExpander;
    private final ArithmeticExpander arithmeticExpander;
    private final CommandSubstitution commandSubstitution;
    private final WordSplitter        wordSplitter;
    private final GlobExpander        globExpander;

    public Expander(Platform platform, BiFunction<String, Environment, String> commandRunner) {
        this.tildeExpander       = new TildeExpander(platform);
        this.parameterExpander   = new ParameterExpander();
        this.arithmeticExpander = new ArithmeticExpander();
        this.commandSubstitution = new CommandSubstitution(commandRunner);
        this.wordSplitter        = new WordSplitter();
        this.globExpander        = new GlobExpander(platform);
    }

    /**
     * Expands a list of AST {@link Word} nodes into a final list of command argument strings.
     *
     * @param words The raw word nodes from the parser.
     * @param env   The shell environment.
     * @return The final list of argument strings ready for execution.
     */
    public List<String> expandWords(List<Word> words, Environment env) {
        List<String> result = new ArrayList<>();
        for (Word word : words) {
            List<String> expanded = expandWord(word, env);
            result.addAll(expanded);
        }
        return result;
    }

    /**
     * Expands a single {@link Word} node into zero, one, or more strings.
     */
    public List<String> expandWord(Word word, Environment env) {
        if (word == null) return List.of();
        List<WordPart> parts = word.parts();
        if (parts == null || parts.isEmpty()) {
            return List.of(word.text());
        }

        // Each element of expandedParts represents one slice: text + whether it can be split/globbed
        List<PartExpansion> expandedParts = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            WordPart part = parts.get(i);
            String content = part.content();

            switch (part.type()) {
                case SINGLE_QUOTED, ANSI_C -> {
                    // Literal: no expansion, no word splitting, no globbing
                    expandedParts.add(new PartExpansion(content, false, false));
                }
                case DOUBLE_QUOTED -> {
                    // Expand variables, command substitution, arithmetic; NO splitting or globbing
                    String exp = expandString(content, env);
                    expandedParts.add(new PartExpansion(exp, false, false));
                }
                case BARE -> {
                    // Tilde expansion (only on first part if at word start)
                    if (i == 0 && content.startsWith("~")) {
                        content = tildeExpander.expand(content, env);
                    }
                    // Expand variables, command substitution, arithmetic
                    String exp = expandString(content, env);
                    // Bare parts CAN undergo word splitting and globbing
                    expandedParts.add(new PartExpansion(exp, true, true));
                }
            }
        }

        // Check if all parts are empty and purely unquoted
        boolean allUnquoted = parts.stream().allMatch(p -> p.type() == WordPart.PartType.BARE);
        boolean allEmpty = expandedParts.stream().allMatch(p -> p.text.isEmpty());
        if (allUnquoted && allEmpty && !word.text().isEmpty()) {
            // Unquoted variable that expanded to empty string disappears entirely (0 words)
            return List.of();
        }

        // Word splitting: check if any splittable bare part contains IFS whitespace
        String ifs = env.isSet("IFS") ? env.get("IFS") : " \t\n";
        boolean hasSplit = false;
        if (ifs != null && !ifs.isEmpty()) {
            for (PartExpansion pe : expandedParts) {
                if (pe.splittable && containsAny(pe.text, ifs)) {
                    hasSplit = true;
                    break;
                }
            }
        }

        List<String> wordsAfterSplit = new ArrayList<>();

        if (!hasSplit) {
            // No splitting required: concatenate all parts into a single string
            StringBuilder sb = new StringBuilder();
            for (PartExpansion pe : expandedParts) {
                sb.append(pe.text);
            }
            wordsAfterSplit.add(sb.toString());
        } else {
            // Split across splittable parts while gluing non-splittable prefixes/suffixes
            List<StringBuilder> currentWords = new ArrayList<>();
            currentWords.add(new StringBuilder());

            for (PartExpansion pe : expandedParts) {
                if (!pe.splittable || !containsAny(pe.text, ifs)) {
                    // Append to the current active word
                    currentWords.get(currentWords.size() - 1).append(pe.text);
                } else {
                    List<String> pieces = wordSplitter.split(pe.text, env);
                    if (pieces.isEmpty()) {
                        // Empty split: continue
                    } else {
                        // Glue first piece to the current word
                        currentWords.get(currentWords.size() - 1).append(pieces.get(0));
                        // Additional pieces start new words
                        for (int k = 1; k < pieces.size(); k++) {
                            StringBuilder next = new StringBuilder();
                            next.append(pieces.get(k));
                            currentWords.add(next);
                        }
                    }
                }
            }

            for (StringBuilder sb : currentWords) {
                wordsAfterSplit.add(sb.toString());
            }
        }

        // Pathname expansion (globbing)
        boolean hasGlobbableParts = expandedParts.stream().anyMatch(p -> p.globbable);
        List<String> finalWords = new ArrayList<>();

        for (String w : wordsAfterSplit) {
            if (hasGlobbableParts && GlobExpander.hasGlobMeta(w)) {
                List<String> matches = globExpander.expand(w, env);
                finalWords.addAll(matches);
            } else {
                finalWords.add(w);
            }
        }

        return finalWords;
    }

    /**
     * Expands variable, command substitution, and arithmetic expansions in a string.
     */
    public String expandString(String s, Environment env) {
        if (s == null || s.isEmpty()) return s;

        // 0. Tilde expansion at string start
        if (s.startsWith("~")) {
            s = tildeExpander.expand(s, env);
        }

        // 1. Parameter expansion
        String result = parameterExpander.expand(s, env);

        // 2. Command substitution $(...) and `...`
        result = commandSubstitution.expand(result, env);

        // 3. Arithmetic expansion $((...))
        result = arithmeticExpander.expand(result, env);

        return result;
    }

    private boolean containsAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private record PartExpansion(String text, boolean splittable, boolean globbable) {}
}
