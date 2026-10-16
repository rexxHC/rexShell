package com.jshell.executor;

/**
 * Thrown when a command cannot be executed (e.g., not found on PATH, permission denied).
 */
public class ExecutionException extends RuntimeException {

    public ExecutionException(String message) {
        super(message);
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
