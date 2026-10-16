package com.jshell.builtin;

import com.jshell.environment.Environment;
import java.io.*;

/** Built-in {@code true}: always succeeds (exits 0). */
public class TrueCommand implements Builtin {
    @Override public String name() { return "true"; }
    @Override public String usage() { return "true"; }
    @Override
    public int execute(String[] args, Environment env,
                       InputStream stdin, OutputStream stdout, OutputStream stderr) {
        return 0;
    }
}
