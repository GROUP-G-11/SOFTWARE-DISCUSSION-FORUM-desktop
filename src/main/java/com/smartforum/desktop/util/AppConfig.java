package com.smartforum.desktop.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Central place for the two things that differ between environments:
 * where the Laravel backend lives, and where this device's local SQLite
 * cache file lives. Both can be overridden without touching code:
 *   - drop a config.properties next to the jar, or on the classpath, with
 *     api.baseUrl=... / db.file=...
 *   - or pass -Dsdf.api.baseUrl=... / -Dsdf.db.file=... as JVM args.
 */
public final class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        // Defaults - point at a locally running `php artisan serve`.
        PROPS.setProperty("api.baseUrl", "http://127.0.0.1:8000/api");
        PROPS.setProperty("db.file", resolveDefaultDbPath());

        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            System.err.println("[AppConfig] Could not read config.properties, using defaults: " + e.getMessage());
        }
    }

    private AppConfig() {
    }

    private static String resolveDefaultDbPath() {
        // Keep the cache in the user's home directory so it survives
        // reinstalls of the app itself and works the same on Windows/macOS/Linux.
        Path home = Path.of(System.getProperty("user.home"), ".smart-discussion-forum");
        return home.resolve("local-cache.sqlite").toString();
    }

    public static String apiBaseUrl() {
        return System.getProperty("sdf.api.baseUrl", PROPS.getProperty("api.baseUrl"));
    }

    /** Base URL with the trailing "/api" stripped - used for websocket/broadcast auth endpoints that don't live under /api. */
    public static String siteBaseUrl() {
        String api = apiBaseUrl();
        return api.endsWith("/api") ? api.substring(0, api.length() - 4) : api;
    }

    public static String dbFile() {
        return System.getProperty("sdf.db.file", PROPS.getProperty("db.file"));
    }
}
