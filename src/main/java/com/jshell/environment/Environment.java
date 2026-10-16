package com.jshell.environment;

import java.nio.file.Path;
import java.util.*;

/**
 * The shell's variable and environment store.
 *
 * <p>Maintains two distinct sets:
 * <ul>
 *   <li><b>Shell variables</b> – local to the shell, not inherited by child processes</li>
 *   <li><b>Exported (environment) variables</b> – passed to child processes via
 *       {@link ProcessBuilder#environment()}</li>
 * </ul>
 *
 * <p>Special read-only variables ({@code $?}, {@code $$}, {@code $!}) are
 * handled as first-class fields for performance, but are also readable via
 * {@link #get(String)}.
 */
public class Environment {

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    /** All shell variables (superset of exported variables). */
    private final Map<String, String> variables = new LinkedHashMap<>();

    /** Names of variables that have been exported to the environment. */
    private final Set<String> exported = new LinkedHashSet<>();

    /** The current working directory tracked by the shell (may differ from JVM's). */
    private Path workingDirectory;

    /** The previous working directory (for {@code cd -}). */
    private Path previousDirectory;

    // -----------------------------------------------------------------------
    // Special variables
    // -----------------------------------------------------------------------

    /** {@code $?} – exit status of the last foreground command. */
    private int lastExitStatus = 0;

    /** {@code $$} – PID of the shell process. */
    private final long shellPid = ProcessHandle.current().pid();

    /** {@code $!} – PID of the last background command. */
    private long lastBackgroundPid = 0;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public Environment() {
        // Inherit the system environment as exported variables
        System.getenv().forEach((k, v) -> {
            variables.put(k, v);
            exported.add(k);
        });
        workingDirectory = Path.of(System.getProperty("user.dir"));
    }

    /**
     * Creates a child environment (for subshells).
     * The child starts with a copy of all variables and the same exported set,
     * but modifications do not affect the parent.
     */
    public Environment fork() {
        Environment child = new Environment();
        child.variables.clear();
        child.exported.clear();
        child.variables.putAll(this.variables);
        child.exported.addAll(this.exported);
        child.workingDirectory  = this.workingDirectory;
        child.previousDirectory = this.previousDirectory;
        child.lastExitStatus    = this.lastExitStatus;
        child.lastBackgroundPid = this.lastBackgroundPid;
        return child;
    }

    // -----------------------------------------------------------------------
    // Variable access
    // -----------------------------------------------------------------------

    /**
     * Gets the value of a shell variable (including special variables).
     *
     * @return The value, or {@code null} if the variable is unset.
     */
    public String get(String name) {
        return switch (name) {
            case "?"  -> String.valueOf(lastExitStatus);
            case "$"  -> String.valueOf(shellPid);
            case "!"  -> String.valueOf(lastBackgroundPid);
            case "PWD" -> workingDirectory.toString();
            case "OLDPWD" -> previousDirectory != null ? previousDirectory.toString() : "";
            default   -> variables.get(name);
        };
    }

    /**
     * Sets a shell variable.
     * If the variable is already exported, the new value is also visible to child processes.
     */
    public void set(String name, String value) {
        variables.put(name, value);
    }

    /**
     * Sets a shell variable and marks it as exported.
     */
    public void export(String name, String value) {
        variables.put(name, value);
        exported.add(name);
    }

    /**
     * Marks an existing variable as exported without changing its value.
     */
    public void export(String name) {
        if (variables.containsKey(name)) {
            exported.add(name);
        }
    }

    /**
     * Unsets a variable (removes it from both the shell and exported sets).
     */
    public void unset(String name) {
        variables.remove(name);
        exported.remove(name);
    }

    /**
     * Returns {@code true} if the variable is set (even if its value is empty).
     */
    public boolean isSet(String name) {
        return variables.containsKey(name) || isSpecial(name);
    }

    /**
     * Returns {@code true} if the variable is marked for export.
     */
    public boolean isExported(String name) {
        return exported.contains(name);
    }

    /**
     * Returns an unmodifiable view of all shell variables.
     */
    public Map<String, String> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * Returns a map containing only the exported variables, suitable for use as
     * a child process environment.
     */
    public Map<String, String> getExportedVariables() {
        Map<String, String> env = new LinkedHashMap<>();
        for (String name : exported) {
            String value = variables.get(name);
            if (value != null) env.put(name, value);
        }
        return env;
    }

    // -----------------------------------------------------------------------
    // Special variable setters
    // -----------------------------------------------------------------------

    public void setLastExitStatus(int status) {
        this.lastExitStatus = status;
        variables.put("?", String.valueOf(status)); // allow $? reads via map too
    }

    public int getLastExitStatus() { return lastExitStatus; }

    public void setLastBackgroundPid(long pid) {
        this.lastBackgroundPid = pid;
    }

    // -----------------------------------------------------------------------
    // Working directory
    // -----------------------------------------------------------------------

    public Path getWorkingDirectory() { return workingDirectory; }

    public void setWorkingDirectory(Path dir) {
        this.previousDirectory = this.workingDirectory;
        this.workingDirectory  = dir;
        variables.put("PWD", dir.toString());
        if (previousDirectory != null) {
            variables.put("OLDPWD", previousDirectory.toString());
        }
    }

    public Path getPreviousDirectory() { return previousDirectory; }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isSpecial(String name) {
        return switch (name) {
            case "?", "$", "!", "PWD", "OLDPWD" -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return "Environment{vars=" + variables.size() + ", exported=" + exported.size()
                + ", cwd=" + workingDirectory + "}";
    }
}
