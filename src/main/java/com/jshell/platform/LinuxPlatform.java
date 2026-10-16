package com.jshell.platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * {@link Platform} implementation for Linux and macOS.
 */
public class LinuxPlatform implements Platform {

    @Override
    public String getPathSeparator() { return ":"; }

    @Override
    public String getFileSeparator() { return "/"; }

    @Override
    public List<String> getExecutableSuffixes() {
        return List.of(""); // No suffix needed on UNIX
    }

    @Override
    public Path getHomeDirectory() {
        String home = System.getenv("HOME");
        if (home != null) return Paths.get(home);
        return Paths.get(System.getProperty("user.home"));
    }

    @Override
    public Optional<String> getEnvVar(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    @Override
    public Optional<Path> resolveExecutable(String name, String pathEnv) {
        if (name.contains("/")) {
            // Explicit path given
            Path p = Paths.get(name);
            if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                return Optional.of(p.toAbsolutePath());
            }
            return Optional.empty();
        }

        if (pathEnv == null || pathEnv.isBlank()) return Optional.empty();

        for (String dir : pathEnv.split(getPathSeparator(), -1)) {
            if (dir.isBlank()) continue;
            for (String suffix : getExecutableSuffixes()) {
                Path candidate = Paths.get(dir, name + suffix);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isExecutable(File file) {
        return file.isFile() && file.canExecute();
    }

    @Override
    public String getNullDevice() { return "/dev/null"; }

    @Override
    public String getLineSeparator() { return "\n"; }

    @Override
    public boolean isWindows() { return false; }

    @Override
    public ProcessBuilder createProcessBuilder(List<String> command) {
        return new ProcessBuilder(command);
    }
}
