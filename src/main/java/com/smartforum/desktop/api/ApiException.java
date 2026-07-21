package com.smartforum.desktop.api;

/**
 * Raised when the server responded but with an error status (validation
 * failure, 401/403, 404, 500, etc). Distinct from {@link ApiOfflineException},
 * which means the request never got a response at all.
 */
public class ApiException extends Exception {
    private final int statusCode;
    private final String rawBody;

    public ApiException(int statusCode, String message, String rawBody) {
        super(message);
        this.statusCode = statusCode;
        this.rawBody = rawBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRawBody() {
        return rawBody;
    }

    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }

    public boolean isValidationError() {
        return statusCode == 422;
    }
}
