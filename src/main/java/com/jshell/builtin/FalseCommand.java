package com.jshell.builtin;

import com.jshell.environment.Environment;
import java.io.*;

/** Built-in {@code false}: always fails (exits 1). */
public class FalseCommand implements Builtin {
    @Override public String name() { return "false"; }
    @Override public String usage() { return "false"; }
    @Override
    public int execute(String[] args, Environment env,
                       InputStream stdin, OutputStream stdout, OutputStream stderr) {
        return 1;
    }
}
