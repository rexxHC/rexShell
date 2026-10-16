package com.jshell.executor;

import com.jshell.builtin.BuiltinRegistry;
import com.jshell.environment.Environment;
import com.jshell.parser.ast.*;
import com.jshell.platform.Platform;

import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * The AST executor — walks a {@link Node} tree and carries out the shell's semantics.
 *
 * <p><b>Phase 1 responsibilities:</b>
 * <ul>
 *   <li>Execute {@link SimpleCommand} nodes (built-ins and external processes)</li>
 *   <li>Execute {@link CommandList} nodes (sequential {@code ;} and background {@code &})</li>
 *   <li>Execute {@link AndOr} nodes with short-circuit evaluation</li>
 *   <li>Execute {@link Pipeline} nodes (wired via OS pipes)</li>
 *   <li>Execute {@link Subshell} nodes (forked environment)</li>
 *   <li>Execute {@link GroupCommand} nodes (same environment)</li>
 *   <li>Apply variable {@link Assignment} nodes</li>
 * </ul>
 *
 * <p>I/O redirection, job control, and expansion are handled in later phases
 * (Phases 3–6). For now, stdin/stdout/stderr are passed through directly.
 */
public class Executor {

    private final Platform        platform;
    private final Environment     environment;
    private final BuiltinRegistry builtins;
    private final ProcessLauncher launcher;

    public Executor(Platform platform, Environment environment, BuiltinRegistry builtins) {
        this.platform    = platform;
        this.environment = environment;
        this.builtins    = builtins;
        this.launcher    = new ProcessLauncher(platform, environment);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Executes an AST node using the default system streams.
     *
     * @param node The root of the AST to execute.
     * @return The exit status of the last executed command.
     */
    public int execute(Node node) {
        return execute(node, System.in, System.out, System.err, null);
    }

    /**
     * Executes an AST node with explicitly provided I/O streams.
     *
     * @param node    The AST node to execute.
     * @param stdin   Standard input for this execution.
     * @param stdout  Standard output for this execution.
     * @param stderr  Standard error for this execution.
     * @param extraEnv Additional env vars for external commands (may be {@code null}).
     * @return Exit status.
     */
    public int execute(Node node,
                       InputStream  stdin,
                       OutputStream stdout,
                       OutputStream stderr,
                       Map<String, String> extraEnv) {

        if (node == null) return 0;

        int status = switch (node) {
            case SimpleCommand sc  -> executeSimpleCommand(sc, stdin, stdout, stderr, extraEnv);
            case CommandList   cl  -> executeCommandList(cl, stdin, stdout, stderr);
            case AndOr         ao  -> executeAndOr(ao, stdin, stdout, stderr);
            case Pipeline      pl  -> executePipeline(pl, stdin, stdout, stderr);
            case Subshell      ss  -> executeSubshell(ss, stdin, stdout, stderr);
            case GroupCommand  gc  -> executeGroupCommand(gc, stdin, stdout, stderr);
            case Assignment    a   -> executeAssignment(a);
            case Redirection   r   -> 0; // Redirections are processed in executeSimpleCommand
        };

        environment.setLastExitStatus(status);
        return status;
    }

    // -----------------------------------------------------------------------
    // Node executors
    // -----------------------------------------------------------------------

    private int executeSimpleCommand(SimpleCommand cmd,
                                     InputStream  stdin,
                                     OutputStream stdout,
                                     OutputStream stderr,
                                     Map<String, String> extraEnv) {
        // Apply any leading assignments
        Map<String, String> tempEnv = new java.util.LinkedHashMap<>();
        for (Assignment a : cmd.assignments()) {
            if (cmd.isAssignmentOnly()) {
                // Pure assignment: set in the shell environment
                environment.set(a.name(), a.value());
            } else {
                // Temporary: only for the child process
                tempEnv.put(a.name(), a.value());
            }
        }
        if (cmd.isAssignmentOnly()) return 0;

        List<String> words = cmd.words();
        if (words.isEmpty()) return 0;

        String name = words.get(0);

        // Try built-in first
        var builtin = builtins.find(name);
        if (builtin.isPresent()) {
            return builtin.get().execute(
                    words.toArray(new String[0]),
                    environment, stdin, stdout, stderr);
        }

        // Fall through to external process
        if (extraEnv != null) tempEnv.putAll(extraEnv);
        try {
            return launcher.launch(words, stdin, stdout, stderr, tempEnv);
        } catch (ExecutionException e) {
            printError(stderr, e.getMessage());
            return 127; // command not found
        }
    }

    private int executeCommandList(CommandList list,
                                   InputStream  stdin,
                                   OutputStream stdout,
                                   OutputStream stderr) {
        int status = 0;
        List<Node>   commands   = list.commands();
        List<String> separators = list.separators();

        for (int i = 0; i < commands.size(); i++) {
            String sep = (i < separators.size()) ? separators.get(i) : ";";
            if ("&".equals(sep)) {
                // Background execution (Phase 6 will add full job control)
                Node cmd = commands.get(i);
                Thread t = Thread.ofVirtual().name("bg-" + i).start(() -> execute(cmd));
                // For Phase 1, record as last background "PID" using thread ID
                environment.setLastBackgroundPid(t.threadId());
                status = 0;
            } else {
                // Sequential
                status = execute(commands.get(i), stdin, stdout, stderr, null);
            }
        }
        return status;
    }

    private int executeAndOr(AndOr ao,
                             InputStream  stdin,
                             OutputStream stdout,
                             OutputStream stderr) {
        int leftStatus = execute(ao.left(), stdin, stdout, stderr, null);
        boolean leftSucceeded = leftStatus == 0;

        if ("&&".equals(ao.operator())) {
            return leftSucceeded ? execute(ao.right(), stdin, stdout, stderr, null) : leftStatus;
        } else { // "||"
            return leftSucceeded ? leftStatus : execute(ao.right(), stdin, stdout, stderr, null);
        }
    }

    private int executePipeline(Pipeline pipeline,
                                InputStream  pipelineStdin,
                                OutputStream pipelineStdout,
                                OutputStream stderr) {
        List<Node> stages = pipeline.commands();
        int n = stages.size();

        // Create n-1 pipe pairs
        PipedOutputStream[] pipeOuts = new PipedOutputStream[n - 1];
        PipedInputStream[]  pipeIns  = new PipedInputStream[n - 1];

        for (int i = 0; i < n - 1; i++) {
            pipeOuts[i] = new PipedOutputStream();
            try {
                pipeIns[i]  = new PipedInputStream(pipeOuts[i], 65536);
            } catch (IOException e) {
                throw new ExecutionException("Failed to create pipe: " + e.getMessage());
            }
        }

        // Launch all stages concurrently
        Thread[] threads  = new Thread[n];
        int[]    statuses = new int[n];

        for (int i = 0; i < n; i++) {
            final int idx = i;
            InputStream  in  = (i == 0)     ? pipelineStdin  : pipeIns[i - 1];
            OutputStream out = (i == n - 1)  ? pipelineStdout : pipeOuts[i];

            threads[i] = Thread.ofVirtual().name("pipe-stage-" + i).start(() -> {
                try {
                    statuses[idx] = execute(stages.get(idx), in, out, stderr, null);
                } finally {
                    // Close our output pipe so next stage sees EOF
                    if (out != pipelineStdout) {
                        try { out.close(); } catch (IOException ignored) {}
                    }
                }
            });
        }

        // Wait for all stages
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        int lastStatus = statuses[n - 1];
        return pipeline.negate() ? (lastStatus == 0 ? 1 : 0) : lastStatus;
    }

    private int executeSubshell(Subshell ss,
                                InputStream  stdin,
                                OutputStream stdout,
                                OutputStream stderr) {
        // Run in a forked environment — changes don't propagate back
        Executor child = new Executor(platform, environment.fork(), builtins);
        return child.execute(ss.body(), stdin, stdout, stderr, null);
    }

    private int executeGroupCommand(GroupCommand gc,
                                    InputStream  stdin,
                                    OutputStream stdout,
                                    OutputStream stderr) {
        // Run in the SAME environment
        return execute(gc.body(), stdin, stdout, stderr, null);
    }

    private int executeAssignment(Assignment a) {
        environment.set(a.name(), a.value());
        return 0;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void printError(OutputStream stderr, String message) {
        try {
            stderr.write(("jshell: " + message + "\n").getBytes());
            stderr.flush();
        } catch (IOException ignored) {}
    }
}
