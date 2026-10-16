package com.jshell.platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * {@link Platform} implementation for Microsoft Windows.
 *
 * <p>Key differences from Linux:
 * <ul>
 *   <li>PATH separator is {@code ;}</li>
 *   <li>Executables may have {@code .exe}, {@code .cmd}, or {@code .bat} suffixes</li>
 *   <li>Null device is {@code NUL}</li>
 *   <li>Executable detection uses PATHEXT rather than the executable bit</li>
 *   <li>Batch scripts need to be invoked through {@code cmd.exe /c}</li>
 * </ul>
 */
public class WindowsPlatform implements Platform {

    /** Suffixes to try when searching for executables. Order matters — .exe first. */
    private static final List<String> EXECUTABLE_SUFFIXES = List.of("", ".exe", ".cmd", ".bat", ".com");

    @Override
    public String getPathSeparator() { return ";"; }

    @Override
    public String getFileSeparator() { return "\\"; }

    @Override
    public List<String> getExecutableSuffixes() {
        return EXECUTABLE_SUFFIXES;
    }

    @Override
    public Path getHomeDirectory() {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null) return Paths.get(userProfile);
        String homeDrive = System.getenv("HOMEDRIVE");
        String homePath  = System.getenv("HOMEPATH");
        if (homeDrive != null && homePath != null) return Paths.get(homeDrive + homePath);
        return Paths.get(System.getProperty("user.home"));
    }

    @Override
    public Optional<String> getEnvVar(String name) {
        // Windows environment variables are case-insensitive; System.getenv handles this
        return Optional.ofNullable(System.getenv(name));
    }

    @Override
    public Optional<Path> resolveExecutable(String name, String pathEnv) {
        // Absolute or relative path with explicit separator
        if (name.contains("\\") || name.contains("/")) {
            Path p = Paths.get(name);
            if (isExecutable(p.toFile())) return Optional.of(p.toAbsolutePath());
            // Try adding suffixes
            for (String suffix : getExecutableSuffixes()) {
                if (suffix.isEmpty()) continue;
                Path withSuffix = Paths.get(name + suffix);
                if (isExecutable(withSuffix.toFile())) return Optional.of(withSuffix.toAbsolutePath());
            }
            return Optional.empty();
        }

        if (pathEnv == null || pathEnv.isBlank()) return Optional.empty();

        // Search PATH entries
        for (String dir : pathEnv.split(getPathSeparator(), -1)) {
            if (dir.isBlank()) continue;
            for (String suffix : getExecutableSuffixes()) {
                Path candidate = Paths.get(dir, name + suffix);
                if (isExecutable(candidate.toFile())) {
                    return Optional.of(candidate.toAbsolutePath());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isExecutable(File file) {
        if (!file.isFile()) return false;
        // On Windows, executability is determined by extension (PATHEXT) not a Unix bit
        String name = file.getName().toLowerCase();
        for (String suffix : EXECUTABLE_SUFFIXES) {
            if (!suffix.isEmpty() && name.endsWith(suffix)) return true;
        }
        return false;
    }

    @Override
    public String getNullDevice() { return "NUL"; }

    @Override
    public String getLineSeparator() { return "\r\n"; }

    @Override
    public boolean isWindows() { return true; }

    @Override
    public ProcessBuilder createProcessBuilder(List<String> command) {
        // Batch/cmd scripts need cmd.exe /c wrapper; .exe files run directly
        String first = command.get(0).toLowerCase();
        if (first.endsWith(".bat") || first.endsWith(".cmd")) {
            List<String> wrapped = new java.util.ArrayList<>();
            wrapped.add("cmd.exe");
            wrapped.add("/c");
            wrapped.addAll(command);
            return new ProcessBuilder(wrapped);
        }
        return new ProcessBuilder(command);
    }
}
