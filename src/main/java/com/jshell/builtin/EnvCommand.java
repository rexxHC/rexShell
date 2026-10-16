package com.jshell.builtin;

import com.jshell.environment.Environment;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Built-in {@code env} command.
 *
 * <p>Usage: {@code env}
 * <ul>
 *   <li>Prints all exported environment variables, one per line in {@code NAME=VALUE} format.</li>
 * </ul>
 */
public class EnvCommand implements Builtin {

    @Override
    public String name() { return "env"; }

    @Override
    public String usage() { return "env"; }

    @Override
    public int execute(String[] args,
                       Environment env,
                       InputStream stdin,
                       OutputStream stdout,
                       OutputStream stderr) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        Map<String, String> exported = env.getExportedVariables();

        exported.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.println(e.getKey() + "=" + e.getValue()));

        out.flush();
        return 0;
    }
}
