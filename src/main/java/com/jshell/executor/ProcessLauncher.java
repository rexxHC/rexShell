package com.jshell.executor;

import com.jshell.environment.Environment;
import com.jshell.platform.Platform;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Launches external (non-built-in) processes using {@link ProcessBuilder}.
 *
 * <p>The launcher:
 * <ul>
 *   <li>Resolves the executable path via {@link Platform#resolveExecutable}</li>
 *   <li>Inherits or overrides the working directory</li>
 *   <li>Populates the child's environment from the shell's exported variables</li>
 *   <li>Wires the provided stdin/stdout/stderr streams</li>
 * </ul>
 */
public class ProcessLauncher {

    private final Platform    platform;
    private final Environment environment;

    public ProcessLauncher(Platform platform, Environment environment) {
        this.platform    = platform;
        this.environment = environment;
    }

    /**
     * Launches an external process and waits for it to complete.
     *
     * @param command     The command and its arguments (already expanded).
     * @param stdin       Stream to feed to the process's standard input.
     * @param stdout      Stream to write the process's standard output to.
     * @param stderr      Stream to write the process's standard error to.
     * @param extraEnv    Additional (transient) environment variables for this invocation only.
     * @return The process exit code.
     * @throws ExecutionException if the executable is not found or the process fails to start.
     */
    public int launch(List<String> command,
                      InputStream  stdin,
                      OutputStream stdout,
                      OutputStream stderr,
                      Map<String, String> extraEnv) throws ExecutionException {

        String cmdName = command.get(0);
        String pathEnv = environment.get("PATH");

        // Resolve the executable
        Path executablePath = platform.resolveExecutable(cmdName, pathEnv)
                .orElseThrow(() -> new ExecutionException(
                        cmdName + ": command not found"));

        // Build the command list using the resolved absolute path
        List<String> resolvedCommand = new java.util.ArrayList<>(command);
        resolvedCommand.set(0, executablePath.toString());

        ProcessBuilder pb = platform.createProcessBuilder(resolvedCommand);

        // Set working directory
        pb.directory(environment.getWorkingDirectory().toFile());

        // Populate environment: start from exported variables then overlay extras
        Map<String, String> env = pb.environment();
        env.clear();
        env.putAll(environment.getExportedVariables());
        if (extraEnv != null) env.putAll(extraEnv);

        // Wire I/O: if the provided stream IS System.in/out/err, inherit; otherwise pipe
        configureIo(pb, stdin, stdout, stderr);

        try {
            Process process = pb.start();

            // If we're piping custom streams, pump them in threads
            Thread stdinPump  = null;
            Thread stdoutPump = null;
            Thread stderrPump = null;

            if (pb.redirectInput()  == ProcessBuilder.Redirect.PIPE && stdin  != System.in) {
                stdinPump = pumpThread("stdin-pump",  stdin,  process.getOutputStream(), true);
                stdinPump.start();
            }
            if (pb.redirectOutput() == ProcessBuilder.Redirect.PIPE && stdout != System.out) {
                stdoutPump = pumpThread("stdout-pump", process.getInputStream(), stdout, false);
                stdoutPump.start();
            }
            if (pb.redirectError()  == ProcessBuilder.Redirect.PIPE && stderr != System.err) {
                stderrPump = pumpThread("stderr-pump", process.getErrorStream(), stderr, false);
                stderrPump.start();
            }

            int exitCode = process.waitFor();

            joinQuietly(stdinPump);
            joinQuietly(stdoutPump);
            joinQuietly(stderrPump);

            return exitCode;

        } catch (IOException e) {
            throw new ExecutionException(cmdName + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130; // conventional SIGINT exit code
        }
    }

    // -----------------------------------------------------------------------
    // I/O wiring
    // -----------------------------------------------------------------------

    private void configureIo(ProcessBuilder pb,
                              InputStream  stdin,
                              OutputStream stdout,
                              OutputStream stderr) {
        if (stdin == System.in) {
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        }

        if (stdout == System.out) {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        }

        if (stderr == System.err) {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
        }
    }

    /** Creates a daemon thread that copies bytes from {@code in} to {@code out}. */
    private Thread pumpThread(String name, InputStream in, OutputStream out, boolean closeOut) {
        Thread t = new Thread(() -> {
            try {
                in.transferTo(out);
                if (closeOut) out.close();
                else out.flush();
            } catch (IOException ignored) {
                // Common when the process closes its stdin early
            }
        }, name);
        t.setDaemon(true);
        return t;
    }

    private void joinQuietly(Thread t) {
        if (t != null) {
            try { t.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
