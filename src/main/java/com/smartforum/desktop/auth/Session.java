package com.smartforum.desktop.auth;

import org.json.JSONArray;
import org.json.JSONObject;

/** Simple in-memory holder for "who is using this running instance of the app right now". */
public class Session {

    private JSONObject currentUser;
    private String token;
    private boolean offlineMode;

    public void set(JSONObject user, String token, boolean offlineMode) {
        this.currentUser = user;
        this.token = token;
        this.offlineMode = offlineMode;
    }

    public void clear() {
        this.currentUser = null;
        this.token = null;
        this.offlineMode = false;
    }

    public boolean isSignedIn() {
        return currentUser != null;
    }

    public JSONObject user() {
        return currentUser;
    }

    public long userId() {
        return currentUser == null ? -1 : currentUser.optLong("user_id", -1);
    }

    public String fullName() {
        return currentUser == null ? "" : currentUser.optString("full_name", "");
    }

    public String token() {
        return token;
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    /** Primary role name (Administrator/Lecturer/Student) taken from the user's roles list, defaulting to Student. */
    public String primaryRole() {
        if (currentUser == null) return "Student";
        JSONArray roles = currentUser.optJSONArray("roles");
        if (roles == null || roles.isEmpty()) return "Student";
        JSONObject first = roles.getJSONObject(0);
        // Server may nest role_name directly or under a "role" object,
        // depending on the eager-load shape - handle both defensively.
        if (first.has("role_name")) return first.getString("role_name");
        JSONObject nested = first.optJSONObject("role");
        return nested != null ? nested.optString("role_name", "Student") : "Student";
    }

    public boolean isAdministrator() {
        return "Administrator".equalsIgnoreCase(primaryRole());
    }

    public boolean isLecturer() {
        return "Lecturer".equalsIgnoreCase(primaryRole());
    }

    public boolean isStudent() {
        return "Student".equalsIgnoreCase(primaryRole());
    }
}
