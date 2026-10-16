package com.jshell.builtin;

import com.jshell.environment.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinTest {

    private Environment env;
    private InputStream stdin;

    @BeforeEach
    void setUp() {
        env   = new Environment();
        stdin = InputStream.nullInputStream();
    }

    // -----------------------------------------------------------------------
    // echo
    // -----------------------------------------------------------------------

    @Test
    void echoBasic() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new EchoCommand().execute(new String[]{"echo", "hello", "world"}, env, stdin, out, System.err);
        assertEquals("hello world\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void echoNoNewline() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new EchoCommand().execute(new String[]{"echo", "-n", "hello"}, env, stdin, out, System.err);
        assertEquals("hello", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void echoEscapeNewline() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new EchoCommand().execute(new String[]{"echo", "-e", "line1\\nline2"}, env, stdin, out, System.err);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("\n"));
    }

    // -----------------------------------------------------------------------
    // exit
    // -----------------------------------------------------------------------

    @Test
    void exitThrowsWithCode() {
        ShellExitException ex = assertThrows(ShellExitException.class, () ->
                new ExitCommand().execute(new String[]{"exit", "42"}, env, stdin, System.out, System.err));
        assertEquals(42, ex.getStatus());
    }

    @Test
    void exitDefaultUsesLastStatus() {
        env.setLastExitStatus(5);
        ShellExitException ex = assertThrows(ShellExitException.class, () ->
                new ExitCommand().execute(new String[]{"exit"}, env, stdin, System.out, System.err));
        assertEquals(5, ex.getStatus());
    }

    // -----------------------------------------------------------------------
    // pwd
    // -----------------------------------------------------------------------

    @Test
    void pwdPrintsWorkingDirectory() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PwdCommand().execute(new String[]{"pwd"}, env, stdin, out, System.err);
        String result = out.toString(StandardCharsets.UTF_8).trim();
        assertFalse(result.isBlank());
        // Should match the environment's tracked working directory
        assertEquals(env.getWorkingDirectory().toString(), result);
    }

    // -----------------------------------------------------------------------
    // cd
    // -----------------------------------------------------------------------

    @Test
    void cdToAbsolutePath() {
        String tempDir = System.getProperty("java.io.tmpdir");
        int status = new CdCommand().execute(
                new String[]{"cd", tempDir}, env, stdin, System.out, System.err);
        assertEquals(0, status);
        assertEquals(java.nio.file.Path.of(tempDir).normalize().toString(), env.getWorkingDirectory().toString());
    }

    @Test
    void cdNonExistentDirectory() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = new CdCommand().execute(
                new String[]{"cd", "/this/does/not/exist/hopefully"}, env, stdin, System.out, err);
        assertNotEquals(0, status);
        assertTrue(err.toString().contains("No such file"));
    }

    @Test
    void cdToOldpwd() {
        String tempDir  = System.getProperty("java.io.tmpdir");
        String startDir = env.getWorkingDirectory().toString();

        // Go somewhere first
        new CdCommand().execute(new String[]{"cd", tempDir}, env, stdin, System.out, System.err);
        // Then cd -
        int status = new CdCommand().execute(new String[]{"cd", "-"}, env, stdin, System.out, System.err);
        assertEquals(0, status);
        assertEquals(startDir, env.getWorkingDirectory().toString());
    }

    // -----------------------------------------------------------------------
    // true / false
    // -----------------------------------------------------------------------

    @Test
    void trueReturnsZero() {
        assertEquals(0, new TrueCommand().execute(new String[]{"true"}, env, stdin, System.out, System.err));
    }

    @Test
    void falseReturnsOne() {
        assertEquals(1, new FalseCommand().execute(new String[]{"false"}, env, stdin, System.out, System.err));
    }

    // -----------------------------------------------------------------------
    // export / unset / env
    // -----------------------------------------------------------------------

    @Test
    void exportSetsAndMarksExported() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = new ExportCommand().execute(
                new String[]{"export", "TEST_VAR=hello_world"}, env, stdin, out, System.err);
        assertEquals(0, status);
        assertEquals("hello_world", env.get("TEST_VAR"));
        assertTrue(env.isExported("TEST_VAR"));
    }

    @Test
    void exportInvalidIdentifier() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = new ExportCommand().execute(
                new String[]{"export", "123INVALID=foo"}, env, stdin, System.out, err);
        assertEquals(1, status);
        assertTrue(err.toString().contains("not a valid identifier"));
    }

    @Test
    void exportListsVariables() {
        env.export("MY_ALPHA", "val1");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = new ExportCommand().execute(
                new String[]{"export"}, env, stdin, out, System.err);
        assertEquals(0, status);
        assertTrue(out.toString().contains("declare -x MY_ALPHA=\"val1\""));
    }

    @Test
    void unsetRemovesVariable() {
        env.export("TO_REMOVE", "123");
        assertTrue(env.isSet("TO_REMOVE"));

        int status = new UnsetCommand().execute(
                new String[]{"unset", "TO_REMOVE"}, env, stdin, System.out, System.err);
        assertEquals(0, status);
        assertFalse(env.isSet("TO_REMOVE"));
        assertNull(env.get("TO_REMOVE"));
    }

    @Test
    void envPrintsExportedVariables() {
        env.export("ENV_TEST_KEY", "ENV_TEST_VAL");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status = new EnvCommand().execute(
                new String[]{"env"}, env, stdin, out, System.err);
        assertEquals(0, status);
        assertTrue(out.toString().contains("ENV_TEST_KEY=ENV_TEST_VAL"));
    }
}
