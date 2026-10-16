package com.jshell.builtin;

import java.util.*;

/**
 * Registry of all built-in shell commands.
 *
 * <p>The registry is populated at shell startup and queried by the {@link com.jshell.executor.Executor}
 * before attempting external process lookup. Built-ins take precedence over executables of
 * the same name on PATH.
 */
public class BuiltinRegistry {

    private final Map<String, Builtin> registry = new LinkedHashMap<>();

    /**
     * Registers a built-in under its primary name and all aliases.
     */
    public void register(Builtin builtin) {
        registry.put(builtin.name(), builtin);
        for (String alias : builtin.aliases()) {
            registry.put(alias, builtin);
        }
    }

    /**
     * Looks up a built-in by name.
     *
     * @return An {@link Optional} containing the built-in, or empty if not found.
     */
    public Optional<Builtin> find(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    /**
     * Returns {@code true} if the given name is a registered built-in.
     */
    public boolean isBuiltin(String name) {
        return registry.containsKey(name);
    }

    /**
     * Returns the names of all registered built-ins (deduplicated, sorted).
     */
    public List<String> names() {
        return registry.keySet().stream().distinct().sorted().toList();
    }

    /**
     * Creates and returns a registry pre-loaded with all Phase 1 built-ins.
     */
    public static BuiltinRegistry createDefault() {
        BuiltinRegistry reg = new BuiltinRegistry();
        reg.register(new EchoCommand());
        reg.register(new ExitCommand());
        reg.register(new TypeCommand(reg));
        reg.register(new CdCommand());
        reg.register(new PwdCommand());
        reg.register(new TrueCommand());
        reg.register(new FalseCommand());
        reg.register(new ExportCommand());
        reg.register(new UnsetCommand());
        reg.register(new EnvCommand());
        return reg;
    }
}
