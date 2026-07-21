package com.smartforum.desktop.api;

/**
 * Raised when a request could not reach the server at all (connection
 * refused, DNS failure, timeout). Callers should treat this as "we're
 * offline" and fall back to {@code LocalStore}'s cached data / outbox queue,
 * rather than showing a generic error.
 */
public class ApiOfflineException extends Exception {
    public ApiOfflineException(String message, Throwable cause) {
        super(message, cause);
    }
}
