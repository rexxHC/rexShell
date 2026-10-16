package com.jshell;

import com.jshell.builtin.BuiltinRegistry;
import com.jshell.builtin.ShellExitException;
import com.jshell.environment.Environment;
import com.jshell.executor.Executor;
import com.jshell.lexer.Lexer;
import com.jshell.lexer.LexerException;
import com.jshell.lexer.Token;
import com.jshell.parser.ParseException;
import com.jshell.parser.Parser;
import com.jshell.parser.ast.Node;
import com.jshell.platform.Platform;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The core shell — wraps the REPL loop and orchestrates
 * {@link Lexer} → {@link Parser} → {@link Executor}.
 *
 * <p>Modes of operation:
 * <ul>
 *   <li><b>Interactive</b>  — reads from stdin with a prompt, using JLine in Phase 5</li>
 *   <li><b>Script</b>       — reads from a file, line by line</li>
 *   <li><b>Command string</b> — executes a single string ({@code -c "…"})</li>
 * </ul>
 */
public class Shell {

    private final Platform        platform;
    private final Environment     environment;
    private final BuiltinRegistry builtins;
    private final Executor        executor;

    public Shell(Platform platform) {
        this.platform    = platform;
        this.environment = new Environment();
        this.builtins    = BuiltinRegistry.createDefault();
        this.executor    = new Executor(platform, environment, builtins);

        // Set default shell variables
        environment.set("SHELL", "jshell");
        environment.set("JSHELL_VERSION", "0.1.0");
        if (environment.get("PS1") == null) {
            environment.set("PS1", "$ ");
        }
    }

    // -----------------------------------------------------------------------
    // Public entry points
    // -----------------------------------------------------------------------

    /**
     * Runs the interactive REPL. Reads from {@link System#in}, prints a prompt
     * to {@link System#out}, and loops until the user exits.
     *
     * <p>Phase 5 will replace the raw BufferedReader with a JLine {@code LineReader}
     * for history, completion, and line editing.
     */
    public void runInteractive() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        boolean isInteractive = System.console() != null;

        while (true) {
            // Print prompt (only when stdin is a terminal)
            if (isInteractive) {
                String ps1 = environment.get("PS1");
                System.out.print(ps1 != null ? ps1 : "$ ");
                System.out.flush();
            }

            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                break;
            }

            // Ctrl+D / EOF
            if (line == null) {
                if (isInteractive) System.out.println();
                break;
            }

            // Blank line or comment-only
            if (line.isBlank()) continue;

            processLine(line);
        }

        System.exit(environment.getLastExitStatus());
    }

    /**
     * Executes a single command string (the {@code -c} mode).
     *
     * @return Exit status.
     */
    public int runCommand(String commandString) {
        return processLine(commandString);
    }

    /**
     * Reads and executes a script file.
     *
     * @param scriptPath Path to the script file.
     * @return Exit status of the last command in the script.
     */
    public int runScript(String scriptPath) throws IOException {
        Path path = environment.getWorkingDirectory().resolve(scriptPath);
        if (!Files.exists(path)) {
            System.err.println("jshell: " + scriptPath + ": No such file or directory");
            return 127;
        }

        List<String> lines = Files.readAllLines(path);
        int status = 0;

        // Multi-line command continuation is not yet supported in Phase 1
        for (String line : lines) {
            // Skip shebang line
            if (line.startsWith("#!")) continue;
            if (line.isBlank()) continue;
            status = processLine(line);
        }
        return status;
    }

    // -----------------------------------------------------------------------
    // Core processing pipeline
    // -----------------------------------------------------------------------

    /**
     * Runs a single line through the full Lex → Parse → Execute pipeline.
     *
     * @return Exit status.
     */
    public int processLine(String line) {
        try {
            // 1. Lex
            Lexer lexer = new Lexer(line);
            List<Token> tokens = lexer.tokenize();

            // 2. Parse
            Parser parser = new Parser(tokens);
            Node ast = parser.parse();

            if (ast == null) return 0; // empty/comment input

            // 3. Execute
            return executor.execute(ast);

        } catch (ShellExitException e) {
            System.exit(e.getStatus());
            return e.getStatus(); // unreachable, appeases the compiler

        } catch (LexerException e) {
            System.err.println("jshell: " + e.getMessage());
            environment.setLastExitStatus(1);
            return 1;

        } catch (ParseException e) {
            System.err.println("jshell: " + e.getMessage());
            environment.setLastExitStatus(2);
            return 2;

        } catch (Exception e) {
            System.err.println("jshell: unexpected error: " + e.getMessage());
            environment.setLastExitStatus(1);
            return 1;
        }
    }

    // -----------------------------------------------------------------------
    // Accessors (for testing and Phase 5+ integrations)
    // -----------------------------------------------------------------------

    public Environment     getEnvironment() { return environment; }
    public BuiltinRegistry getBuiltins()    { return builtins;    }
    public Platform        getPlatform()    { return platform;     }
    public Executor        getExecutor()    { return executor;     }
}
