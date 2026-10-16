package com.jshell.platform;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Abstracts operating-system-specific behavior so that the rest of the shell
 * can remain platform-agnostic.
 *
 * <p>Two implementations are provided:
 * <ul>
 *   <li>{@link LinuxPlatform} — for Linux/macOS/UNIX</li>
 *   <li>{@link WindowsPlatform} — for Windows</li>
 * </ul>
 *
 * <p>The appropriate implementation is selected at startup via {@link PlatformFactory}.
 */
public interface Platform {

    /**
     * Returns the OS-specific path separator character (e.g., {@code ':'} on UNIX,
     * {@code ';'} on Windows).
     */
    String getPathSeparator();

    /**
     * Returns the OS-specific file separator character ({@code '/'} or {@code '\\'}).
     */
    String getFileSeparator();

    /**
     * Returns the suffix appended to executable names when searching PATH
     * (empty on UNIX, {@code ".exe"}, {@code ".cmd"}, {@code ".bat"} on Windows).
     */
    List<String> getExecutableSuffixes();

    /**
     * Returns the user's home directory.
     */
    Path getHomeDirectory();

    /**
     * Returns the value of an environment variable, or {@code Optional.empty()} if not set.
     */
    Optional<String> getEnvVar(String name);

    /**
     * Searches the given {@code PATH} string for an executable named {@code name}.
     *
     * @param name The command name (no path separators, no suffix).
     * @param path The {@code PATH} value to search (colon- or semicolon-separated).
     * @return The resolved absolute path, or {@code Optional.empty()} if not found.
     */
    Optional<Path> resolveExecutable(String name, String path);

    /**
     * Returns {@code true} if the given file is considered an executable on this platform.
     */
    boolean isExecutable(File file);

    /**
     * Returns the name of the null device ({@code /dev/null} on UNIX, {@code NUL} on Windows).
     */
    String getNullDevice();

    /**
     * Returns the line separator for this platform.
     */
    String getLineSeparator();

    /**
     * Returns {@code true} if this platform is Windows.
     */
    boolean isWindows();

    /**
     * Returns a {@link ProcessBuilder} pre-configured for the platform.
     * On Windows, wraps commands via {@code cmd.exe /c} when needed.
     */
    ProcessBuilder createProcessBuilder(List<String> command);
}
