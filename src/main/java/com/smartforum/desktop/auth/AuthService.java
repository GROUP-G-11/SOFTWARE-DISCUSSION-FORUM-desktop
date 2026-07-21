package com.smartforum.desktop.auth;

import com.smartforum.desktop.api.ApiClient;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import com.smartforum.desktop.db.LocalStore;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authenticate User use case (SDD Table 29), including the offline
 * alternative flow: "If no network is found, the Java desktop client looks
 * up the locally stored cryptographic hash in its local SQLite database."
 *
 * The server never sends us the real password_hash, so what we store
 * locally is our own salted SHA-256 hash of the password, written only
 * after a successful *online* login. Offline sign-in then just needs to
 * reproduce that same hash from what was typed and compare - it never
 * grants a fresh server session, only restores the last cached identity so
 * the person can keep reading what's already synced locally.
 */
public class AuthService {

    private final ApiClient api;
    private final LocalStore store;
    private final Session session;

    public AuthService(ApiClient api, LocalStore store, Session session) {
        this.api = api;
        this.store = store;
        this.session = session;
    }

    public JSONObject register(String fullName, String email, String password, String passwordConfirmation, String role) throws ApiException, ApiOfflineException {
        JSONObject response = api.register(fullName, email, password, passwordConfirmation, role);
        applySuccessfulOnlineLogin(response, password);
        return response;
    }

    /**
     * Tries the real server login first. If the network is unreachable,
     * falls back to the offline hash check against the last cached login
     * for this email, per the SDD's offline authentication alternative flow.
     */
    public LoginOutcome login(String email, String password) throws ApiException {
        try {
            JSONObject response = api.login(email, password);
            applySuccessfulOnlineLogin(response, password);
            return LoginOutcome.ONLINE;
        } catch (ApiOfflineException offline) {
            return attemptOfflineLogin(email, password);
        }
    }

    private LoginOutcome attemptOfflineLogin(String email, String password) {
        String storedSalt = store.getMeta("auth_salt:" + email);
        String storedHash = store.getMeta("auth_hash:" + email);
        String cachedUserJson = store.getMeta("auth_user:" + email);
        String cachedToken = store.getMeta("auth_token:" + email);

        if (storedSalt == null || storedHash == null || cachedUserJson == null) {
            return LoginOutcome.OFFLINE_NO_CACHED_ACCOUNT;
        }

        String computedHash = hash(password, storedSalt);
        if (!computedHash.equals(storedHash)) {
            return LoginOutcome.OFFLINE_BAD_PASSWORD;
        }

        JSONObject cachedUser = new JSONObject(cachedUserJson);
        session.set(cachedUser, cachedToken, true);
        api.setBearerToken(cachedToken);
        return LoginOutcome.OFFLINE_OK;
    }

    private void applySuccessfulOnlineLogin(JSONObject response, String password) {
        JSONObject user = response.getJSONObject("user");
        String token = response.getString("token");

        session.set(user, token, false);
        api.setBearerToken(token);

        // Persist an offline-verification hash + cached identity, keyed by
        // email, so this account can still open (read-only, from cache) the
        // next time there's no network at all.
        String email = user.optString("email");
        String salt = generateSalt();
        store.setMeta("auth_salt:" + email, salt);
        store.setMeta("auth_hash:" + email, hash(password, salt));
        store.setMeta("auth_user:" + email, user.toString());
        store.setMeta("auth_token:" + email, token);
    }

    public void logout() {
        try {
            if (!session.isOfflineMode()) {
                api.logout();
            }
        } catch (ApiException | ApiOfflineException ignored) {
            // Logging out is best-effort - clear local session regardless.
        } finally {
            session.clear();
            api.setBearerToken(null);
        }
    }

    private static String generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on any real JVM; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public enum LoginOutcome {
        ONLINE,
        OFFLINE_OK,
        OFFLINE_NO_CACHED_ACCOUNT,
        OFFLINE_BAD_PASSWORD
    }
}
