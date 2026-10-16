package com.jshell;

import com.jshell.platform.Platform;
import com.jshell.platform.PlatformFactory;

import java.io.IOException;

/**
 * Entry point for JShell.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code java -jar jshell.jar}           — interactive mode</li>
 *   <li>{@code java -jar jshell.jar script.sh}  — script mode</li>
 *   <li>{@code java -jar jshell.jar -c "cmd"}   — command string mode</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) throws IOException {
        Platform platform = PlatformFactory.detect();
        Shell shell = new Shell(platform);

        if (args.length == 0) {
            // Interactive mode
            shell.runInteractive();
        } else if (args.length >= 2 && args[0].equals("-c")) {
            // Command string mode: -c "command string"
            String command = args[1];
            int exit = shell.runCommand(command);
            System.exit(exit);
        } else {
            // Script mode: first arg is a script file path
            String scriptPath = args[0];
            int exit = shell.runScript(scriptPath);
            System.exit(exit);
        }
    }
}
