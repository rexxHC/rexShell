package com.jshell.expansion;

import com.jshell.environment.Environment;
import com.jshell.platform.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles tilde expansion (~, ~/path, ~+, ~-, ~user).
 *
 * <p>In bash:
 * <ul>
 *   <li>{@code ~} or {@code ~/} expands to {@code $HOME}</li>
 *   <li>{@code ~+} expands to {@code $PWD}</li>
 *   <li>{@code ~-} expands to {@code $OLDPWD}</li>
 *   <li>{@code ~username} expands to the home directory of username</li>
 * </ul>
 */
public class TildeExpander {

    private final Platform platform;

    public TildeExpander(Platform platform) {
        this.platform = platform;
    }

    /**
     * Performs tilde expansion on an unquoted string.
     *
     * @param word The word string to inspect.
     * @param env  The shell environment.
     * @return The tilde-expanded string, or the original string if not applicable.
     */
    public String expand(String word, Environment env) {
        if (word == null || !word.startsWith("~")) {
            return word;
        }

        // Find the boundary of the tilde-prefix (up to the first '/' or end of word)
        int slash = word.indexOf('/');
        String prefix = (slash >= 0) ? word.substring(0, slash) : word;
        String remainder = (slash >= 0) ? word.substring(slash) : "";

        String home = resolvePrefix(prefix, env);
        if (home != null) {
            return home + remainder;
        }

        return word;
    }

    /**
     * Resolves the prefix part of a tilde expansion (e.g. "~", "~+", "~-", "~user").
     */
    private String resolvePrefix(String prefix, Environment env) {
        if ("~".equals(prefix)) {
            String home = env.get("HOME");
            if (home != null && !home.isBlank()) {
                return home;
            }
            return platform.getHomeDirectory().toString();
        }

        if ("~+".equals(prefix)) {
            String pwd = env.get("PWD");
            return (pwd != null) ? pwd : env.getWorkingDirectory().toString();
        }

        if ("~-".equals(prefix)) {
            String oldpwd = env.get("OLDPWD");
            return (oldpwd != null) ? oldpwd : env.getPreviousDirectory() != null ? env.getPreviousDirectory().toString() : null;
        }

        // ~username: look up home directory of that user
        String username = prefix.substring(1);
        if (!username.isEmpty()) {
            // Check if it's current user
            String currentUser = System.getProperty("user.name");
            if (username.equals(currentUser)) {
                return platform.getHomeDirectory().toString();
            }

            // On Linux/Unix, standard home is /home/username or /root
            if (!platform.isWindows()) {
                if ("root".equals(username)) {
                    Path rootHome = Paths.get("/root");
                    if (Files.exists(rootHome)) return rootHome.toString();
                }
                Path userHome = Paths.get("/home", username);
                if (Files.exists(userHome)) {
                    return userHome.toString();
                }
            } else {
                // On Windows: C:/Users/username
                String userProfile = System.getenv("USERPROFILE");
                if (userProfile != null) {
                    Path parent = Paths.get(userProfile).getParent();
                    if (parent != null) {
                        Path candidate = parent.resolve(username);
                        if (Files.exists(candidate)) {
                            return candidate.toString();
                        }
                    }
                }
            }
        }

        return null;
    }
}
