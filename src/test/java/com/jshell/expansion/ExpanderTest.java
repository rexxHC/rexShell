package com.jshell.expansion;

import com.jshell.environment.Environment;
import com.jshell.parser.ast.Word;
import com.jshell.platform.PlatformFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpanderTest {

    private Environment env;
    private Expander expander;

    @BeforeEach
    void setUp() {
        env = new Environment();
        expander = new Expander(PlatformFactory.detect(), (cmd, subEnv) -> {
            if (cmd.startsWith("echo ")) {
                return cmd.substring(5);
            }
            return "";
        });
    }

    // -----------------------------------------------------------------------
    // Parameter Expansions
    // -----------------------------------------------------------------------

    @Test
    void simpleVariableExpansion() {
        env.set("GREETING", "hello");
        assertEquals("hello world", expander.expandString("$GREETING world", env));
        assertEquals("hello world", expander.expandString("${GREETING} world", env));
    }

    @Test
    void defaultValues() {
        // Unset
        assertEquals("fallback", expander.expandString("${UNSET_VAR:-fallback}", env));
        // Empty
        env.set("EMPTY_VAR", "");
        assertEquals("fallback", expander.expandString("${EMPTY_VAR:-fallback}", env));
        // Set
        env.set("SET_VAR", "actual");
        assertEquals("actual", expander.expandString("${SET_VAR:-fallback}", env));
    }

    @Test
    void assignDefaultValues() {
        assertFalse(env.isSet("NEW_VAR"));
        assertEquals("assigned", expander.expandString("${NEW_VAR:=assigned}", env));
        assertTrue(env.isSet("NEW_VAR"));
        assertEquals("assigned", env.get("NEW_VAR"));
    }

    @Test
    void alternativeValues() {
        assertEquals("", expander.expandString("${UNSET_VAR:+alternative}", env));
        env.set("SET_VAR", "value");
        assertEquals("alternative", expander.expandString("${SET_VAR:+alternative}", env));
    }

    @Test
    void stringLength() {
        env.set("TEXT", "abcdef");
        assertEquals("6", expander.expandString("${#TEXT}", env));
    }

    @Test
    void substringSlicing() {
        env.set("VAR", "0123456789");
        assertEquals("3456789", expander.expandString("${VAR:3}", env));
        assertEquals("345", expander.expandString("${VAR:3:3}", env));
    }

    @Test
    void patternRemovalPrefix() {
        env.set("PATH_VAR", "/usr/local/bin");
        assertEquals("usr/local/bin", expander.expandString("${PATH_VAR#/}", env));
        assertEquals("local/bin", expander.expandString("${PATH_VAR#/*/}", env));
        assertEquals("bin", expander.expandString("${PATH_VAR##/*/}", env));
    }

    @Test
    void patternRemovalSuffix() {
        env.set("FILE", "archive.tar.gz");
        assertEquals("archive.tar", expander.expandString("${FILE%.*}", env));
        assertEquals("archive", expander.expandString("${FILE%%.*}", env));
    }

    @Test
    void patternSubstitution() {
        env.set("FRUIT", "apple banana apple");
        assertEquals("orange banana apple", expander.expandString("${FRUIT/apple/orange}", env));
        assertEquals("orange banana orange", expander.expandString("${FRUIT//apple/orange}", env));
    }

    @Test
    void specialParameters() {
        env.setLastExitStatus(42);
        assertEquals("42", expander.expandString("$?", env));
        assertNotNull(expander.expandString("$$", env));
    }

    // -----------------------------------------------------------------------
    // Arithmetic Expansions
    // -----------------------------------------------------------------------

    @Test
    void basicArithmetic() {
        assertEquals("5", expander.expandString("$((2 + 3))", env));
        assertEquals("14", expander.expandString("$((2 + 3 * 4))", env));
        assertEquals("20", expander.expandString("$(((2 + 3) * 4))", env));
        assertEquals("8", expander.expandString("$((2 ** 3))", env));
    }

    @Test
    void arithmeticWithVariables() {
        env.set("X", "10");
        env.set("Y", "5");
        assertEquals("15", expander.expandString("$((X + Y))", env));
        assertEquals("15", expander.expandString("$(($X + $Y))", env));
    }

    @Test
    void arithmeticTernaryAndComparisons() {
        assertEquals("100", expander.expandString("$((5 > 3 ? 100 : 200))", env));
        assertEquals("200", expander.expandString("$((5 < 3 ? 100 : 200))", env));
        assertEquals("1", expander.expandString("$((4 == 4))", env));
        assertEquals("0", expander.expandString("$((4 != 4))", env));
    }

    // -----------------------------------------------------------------------
    // Command Substitution
    // -----------------------------------------------------------------------

    @Test
    void commandSubstitutionBasic() {
        assertEquals("hello world", expander.expandString("$(echo hello world)", env));
    }

    // -----------------------------------------------------------------------
    // Tilde Expansion
    // -----------------------------------------------------------------------

    @Test
    void tildeExpandsHome() {
        env.set("HOME", "/custom/home");
        assertEquals("/custom/home", expander.expandString("~", env));
        assertEquals("/custom/home/docs", expander.expandString("~/docs", env));
    }

    // -----------------------------------------------------------------------
    // Word Splitting & Quoting
    // -----------------------------------------------------------------------

    @Test
    void unquotedVariableIsWordSplit() {
        env.set("ITEMS", "one two three");
        Word word = Word.bare("$ITEMS");
        List<String> words = expander.expandWord(word, env);
        assertEquals(List.of("one", "two", "three"), words);
    }

    @Test
    void doubleQuotedVariableIsNotWordSplit() {
        env.set("ITEMS", "one two three");
        Word word = new Word("\"$ITEMS\"", List.of(
                new com.jshell.lexer.WordPart(com.jshell.lexer.WordPart.PartType.DOUBLE_QUOTED, "$ITEMS")
        ));
        List<String> words = expander.expandWord(word, env);
        assertEquals(List.of("one two three"), words);
    }

    @Test
    void singleQuotedStringHasNoExpansion() {
        env.set("VAR", "expanded");
        Word word = new Word("'$VAR'", List.of(
                new com.jshell.lexer.WordPart(com.jshell.lexer.WordPart.PartType.SINGLE_QUOTED, "$VAR")
        ));
        List<String> words = expander.expandWord(word, env);
        assertEquals(List.of("$VAR"), words);
    }

    // -----------------------------------------------------------------------
    // Globbing
    // -----------------------------------------------------------------------

    @Test
    void globExpandsFiles() {
        // pom.xml exists in the current directory
        GlobExpander glob = new GlobExpander(PlatformFactory.detect());
        List<String> matches = glob.expand("pom.*", env);
        assertTrue(matches.contains("pom.xml"));
    }

    @Test
    void nonMatchingGlobRemainsLiteral() {
        GlobExpander glob = new GlobExpander(PlatformFactory.detect());
        List<String> matches = glob.expand("*.non_existent_extension_xyz", env);
        assertEquals(List.of("*.non_existent_extension_xyz"), matches);
    }
}
