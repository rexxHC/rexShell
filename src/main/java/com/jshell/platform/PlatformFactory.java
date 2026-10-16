package com.jshell.platform;

/**
 * Auto-detects the current operating system and returns the appropriate {@link Platform}.
 */
public final class PlatformFactory {

    private PlatformFactory() {}

    /**
     * Returns a {@link Platform} instance suited to the current operating system.
     *
     * <p>Detection is based on the {@code os.name} system property.
     */
    public static Platform detect() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return new WindowsPlatform();
        }
        // Linux, macOS, BSD, Solaris all use the UNIX-style platform
        return new LinuxPlatform();
    }
}
