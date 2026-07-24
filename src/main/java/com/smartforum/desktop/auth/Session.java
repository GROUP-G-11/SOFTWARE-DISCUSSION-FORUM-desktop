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

    /**
     * Primary role name (Administrator/Lecturer/Student).
     *
     * The server (AuthController) already computes this with the correct
     * priority - Administrator > Lecturer > Student - across ALL of the
     * user's role rows, and sends it as a flat "role" string on the user
     * payload. We must use that value directly rather than re-deriving it
     * from the raw "roles" relation array, because that array is unordered:
     * taking roles[0] picks whichever role row the DB happened to return
     * first, not the highest-priority one, which silently downgrades
     * admins/lecturers who also hold a lower-priority role row.
     */
    public String primaryRole() {
        if (currentUser == null) return "Student";

        String flatRole = currentUser.optString("role", null);
        if (flatRole != null && !flatRole.isEmpty()) {
            return flatRole;
        }

        // Fallback for any older cached payload that predates the flat
        // "role" field: derive priority manually across ALL roles,
        // instead of just taking the first entry.
        JSONArray roles = currentUser.optJSONArray("roles");
        if (roles == null || roles.isEmpty()) return "Student";

        boolean hasAdmin = false;
        boolean hasLecturer = false;
        for (int i = 0; i < roles.length(); i++) {
            JSONObject entry = roles.getJSONObject(i);
            String name = entry.has("role_name")
                    ? entry.optString("role_name", "")
                    : (entry.optJSONObject("role") != null
                    ? entry.optJSONObject("role").optString("role_name", "")
                    : "");
            if ("Administrator".equalsIgnoreCase(name)) hasAdmin = true;
            if ("Lecturer".equalsIgnoreCase(name)) hasLecturer = true;
        }
        if (hasAdmin) return "Administrator";
        if (hasLecturer) return "Lecturer";
        return "Student";
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
