package com.smartforum.desktop.db;

import org.json.JSONObject;

/** One queued offline action waiting to be replayed against the server. */
public record OutboxEntry(long id, String kind, JSONObject payload, String createdAt, String errorMessage, int retryCount) {
}