package com.smartforum.desktop.api;

import com.smartforum.desktop.util.AppConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Thin REST wrapper around the Laravel backend (see routes/api.php on the
 * server for the source of truth this class mirrors). Every dashboard panel
 * goes through here rather than building HTTP requests itself, so the token
 * header, base URL, timeouts, and error handling live in exactly one place.
 *
 * Network failures are surfaced as {@link ApiOfflineException} specifically
 * (as opposed to {@link ApiException} for a real HTTP error response), so
 * calling code can tell "the server said no" apart from "there's no network
 * right now" and fall back to the local SQLite cache / outbox accordingly.
 */
public class ApiClient {

    private final HttpClient http;
    private final String baseUrl;
    private volatile String bearerToken;

    public ApiClient() {
        this.baseUrl = AppConfig.apiBaseUrl();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public void setBearerToken(String token) {
        this.bearerToken = token;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public boolean isAuthenticated() {
        return bearerToken != null && !bearerToken.isBlank();
    }

    // ------------------------------------------------------------------
    // 5.1 Membership and On-boarding
    // ------------------------------------------------------------------

    public JSONObject register(String fullName, String email, String password, String passwordConfirmation, String role) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject()
                .put("full_name", fullName)
                .put("email", email)
                .put("password", password)
                .put("password_confirmation", passwordConfirmation)
                .put("rules_accepted", true);
        if (role != null) {
            body.put("role", role);
        }
        return postJson("/register", body);
    }

    public JSONObject login(String email, String password) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("email", email).put("password", password);
        return postJson("/login", body);
    }

    public void logout() throws ApiException, ApiOfflineException {
        postJson("/logout", new JSONObject());
    }

    public JSONObject me() throws ApiException, ApiOfflineException {
        return getJson("/me");
    }

    public JSONObject updateProfile(Map<String, Object> fields) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject(fields);
        return patchJson("/me", body);
    }
    public JSONObject uploadAvatar(File file) throws ApiException, ApiOfflineException {
        return new JSONObject(multipartPost("/me/avatar", "avatar", file));
    }

    // ------------------------------------------------------------------
    // Role Management (Administrator only) - SDD Table 30
    // ------------------------------------------------------------------

    public JSONObject listUsers(String search) throws ApiException, ApiOfflineException {
        String path = "/users";
        if (search != null && !search.isBlank()) path += "?search=" + urlEncode(search);
        return getJson(path);
    }

    public JSONObject getUser(long userId) throws ApiException, ApiOfflineException {
        return getJson("/users/" + userId);
    }

    public JSONObject assignRole(long userId, String role) throws ApiException, ApiOfflineException {
        return patchJson("/users/" + userId + "/role", new JSONObject().put("role", role));
    }

    // ------------------------------------------------------------------
    // Groups
    // ------------------------------------------------------------------

    public JSONObject listGroups() throws ApiException, ApiOfflineException {
        return getJson("/groups");
    }

    public JSONObject createGroup(String name, String description, Integer inactivityWarningDays, Integer blacklistDurationDays) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("name", name).put("description", description == null ? JSONObject.NULL : description);
        if (inactivityWarningDays != null) body.put("inactivity_warning_period", inactivityWarningDays);
        if (blacklistDurationDays != null) body.put("blacklist_duration_days", blacklistDurationDays);
        return postJson("/groups", body);
    }

    public JSONObject getGroup(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId);
    }

    public JSONObject joinGroup(long groupId) throws ApiException, ApiOfflineException {
        return postJson("/groups/" + groupId + "/join", new JSONObject().put("rules_accepted", true));
    }

    public JSONObject groupMembers(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId + "/members");
    }

    // ------------------------------------------------------------------
    // 5.3 Topic Management and Export
    // ------------------------------------------------------------------

    public JSONObject listTopics(long groupId, String search, String category) throws ApiException, ApiOfflineException {
        StringBuilder q = new StringBuilder("/groups/" + groupId + "/topics");
        String sep = "?";
        if (search != null && !search.isBlank()) { q.append(sep).append("search=").append(urlEncode(search)); sep = "&"; }
        if (category != null && !category.isBlank()) { q.append(sep).append("category=").append(urlEncode(category)); }
        return getJson(q.toString());
    }

    public JSONArray topicCategories(long groupId) throws ApiException, ApiOfflineException {
        return getJsonArray("/groups/" + groupId + "/topics/categories");
    }

    public JSONObject createTopic(long groupId, String title, String clientRef) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("title", title);
        if (clientRef != null) body.put("client_ref", clientRef);
        return postJson("/groups/" + groupId + "/topics", body);
    }

    public JSONObject getTopic(long topicId) throws ApiException, ApiOfflineException {
        return getJson("/topics/" + topicId);
    }

    public JSONObject topicPosts(long topicId) throws ApiException, ApiOfflineException {
        return getJson("/topics/" + topicId + "/posts");
    }

    public String downloadTopicPdfUrl(long topicId) {
        return baseUrl + "/topics/" + topicId + "/download-pdf";
    }

    /**
     * Downloads the exported PDF for a topic and returns the raw bytes.
     *
     * The endpoint requires the same {@code Authorization: Bearer <token>}
     * header every other call uses, which a plain "open this URL in the
     * system browser" approach can't send - the browser has no way to know
     * the app's session token, so that link 401s (or the OS simply has no
     * default browser configured, e.g. on a fresh machine or over remote
     * desktop). Fetching the bytes here, over the same authenticated
     * HttpClient, and saving them to a local file is what actually works.
     */
    public byte[] downloadTopicPdf(long topicId) throws ApiException, ApiOfflineException {
        String path = "/topics/" + topicId + "/download-pdf";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/pdf")
                .GET();

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<byte[]> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new ApiOfflineException("Request to " + path + " timed out.", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiOfflineException("Could not reach the server for " + path + ".", e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }

        String message = extractMessage(new String(response.body(), StandardCharsets.UTF_8));
        throw new ApiException(status, message, null);
    }

    // ------------------------------------------------------------------
    // Posts & replies
    // ------------------------------------------------------------------

    public JSONObject createPost(long topicId, String content, String attachmentUrl, long[] excludeUserIds, String clientRef) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("content", content);
        if (attachmentUrl != null) body.put("attachment_url", attachmentUrl);
        if (excludeUserIds != null && excludeUserIds.length > 0) body.put("exclude_user_ids", excludeUserIds);
        if (clientRef != null) body.put("client_ref", clientRef);
        return postJson("/topics/" + topicId + "/posts", body);
    }

    public void deletePost(long postId) throws ApiException, ApiOfflineException {
        deleteJson("/posts/" + postId);
    }

    public JSONObject flagPost(long postId) throws ApiException, ApiOfflineException {
        return postJson("/posts/" + postId + "/flag", new JSONObject());
    }

    public JSONObject createReply(long postId, String content, String clientRef) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("content", content);
        if (clientRef != null) body.put("client_ref", clientRef);
        return postJson("/posts/" + postId + "/replies", body);
    }

    public JSONObject flagReply(long replyId) throws ApiException, ApiOfflineException {
        return postJson("/replies/" + replyId + "/flag", new JSONObject());
    }

    // ------------------------------------------------------------------
    // 5.2 Moderation and Inactivity Management
    // ------------------------------------------------------------------

    public JSONArray listWarnings() throws ApiException, ApiOfflineException {
        return getJsonArray("/moderation/warnings");
    }

    public JSONObject scanInactivity(long groupId) throws ApiException, ApiOfflineException {
        return postJson("/groups/" + groupId + "/moderation/scan-inactivity", new JSONObject());
    }

    public JSONObject resolveWarning(long warningId) throws ApiException, ApiOfflineException {
        return postJson("/moderation/warnings/" + warningId + "/resolve", new JSONObject());
    }

    public JSONObject blacklistUser(long groupId, long userId) throws ApiException, ApiOfflineException {
        return postJson("/groups/" + groupId + "/blacklist/" + userId, new JSONObject());
    }

    public JSONArray listBlacklists() throws ApiException, ApiOfflineException {
        return getJsonArray("/moderation/blacklists");
    }

    public JSONObject liftBlacklist(long blacklistId) throws ApiException, ApiOfflineException {
        return postJson("/moderation/blacklists/" + blacklistId + "/lift", new JSONObject());
    }

    // ------------------------------------------------------------------
    // 5.4 Messaging and Synchronization
    // ------------------------------------------------------------------

    /**
     * Push queued offline actions (if any) and pull everything new since
     * lastSyncedAt. Mirrors SyncController::sync() exactly: same field
     * names, same response shape.
     */
    public JSONObject sync(String lastSyncedAtIso, JSONArray queuedActions) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("device_type", "Desktop");
        if (lastSyncedAtIso != null) body.put("last_synced_at", lastSyncedAtIso);
        if (queuedActions != null && !queuedActions.isEmpty()) body.put("queued_actions", queuedActions);
        return postJson("/sync", body);
    }

    // ------------------------------------------------------------------
    // 5.5 Quiz Engine
    // ------------------------------------------------------------------

    public JSONObject listGroupQuizzes(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId + "/quizzes");
    }

    public JSONObject getQuiz(long quizId) throws ApiException, ApiOfflineException {
        return getJson("/quizzes/" + quizId);
    }

    public JSONArray myQuizzes() throws ApiException, ApiOfflineException {
        return getJsonArray("/me/quizzes");
    }

    public JSONObject createQuiz(long groupId, JSONObject quizPayload) throws ApiException, ApiOfflineException {
        return postJson("/groups/" + groupId + "/quizzes", quizPayload);
    }

    public JSONObject publishQuiz(long quizId) throws ApiException, ApiOfflineException {
        return postJson("/quizzes/" + quizId + "/publish", new JSONObject());
    }

    public JSONObject closeQuiz(long quizId) throws ApiException, ApiOfflineException {
        return postJson("/quizzes/" + quizId + "/close", new JSONObject());
    }

    public JSONObject quizResults(long quizId) throws ApiException, ApiOfflineException {
        return getJson("/quizzes/" + quizId + "/results");
    }

    public JSONObject startAttempt(long quizId) throws ApiException, ApiOfflineException {
        return postJson("/quizzes/" + quizId + "/attempts/start", new JSONObject());
    }

    public JSONObject submitAttempt(long attemptId, JSONArray answers, boolean autoSubmitted) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject().put("answers", answers).put("auto_submitted", autoSubmitted);
        return postJson("/attempts/" + attemptId + "/submit", body);
    }

    public JSONArray myQuizAttempts() throws ApiException, ApiOfflineException {
        return getJsonArray("/me/quiz-attempts");
    }

    public JSONObject myAttemptFor(long quizId) throws ApiException, ApiOfflineException {
        return getJson("/quizzes/" + quizId + "/my-attempt");
    }

    // ------------------------------------------------------------------
    // 5.6 Grading and Participation
    // ------------------------------------------------------------------

    public JSONObject leaderboard(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId + "/leaderboard");
    }

    public JSONObject myGrade(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId + "/my-grade");
    }

    public JSONArray scoringCriteria(long groupId) throws ApiException, ApiOfflineException {
        return getJsonArray("/groups/" + groupId + "/scoring-criteria");
    }

    public JSONObject addScoringCriteria(long groupId, String description, String activityType, double maxMarks) throws ApiException, ApiOfflineException {
        JSONObject body = new JSONObject()
                .put("description", description)
                .put("activity_type", activityType)
                .put("max_marks", maxMarks);
        return postJson("/groups/" + groupId + "/scoring-criteria", body);
    }

    public JSONObject gradebook(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId + "/gradebook");
    }

    // ------------------------------------------------------------------
    // 5.7 Statistics
    // ------------------------------------------------------------------

    public JSONObject systemStatistics() throws ApiException, ApiOfflineException {
        return getJson("/statistics/system");
    }

    public JSONObject groupStatistics(long groupId) throws ApiException, ApiOfflineException {
        return getJson("/groups/" + groupId + "/statistics");
    }

    // ------------------------------------------------------------------
    // 5.8 ML Recommendations
    // ------------------------------------------------------------------

    public JSONArray recommendations() throws ApiException, ApiOfflineException {
        return getJsonArray("/recommendations");
    }

    // ------------------------------------------------------------------
    // 5.9 Social Media Sharing
    // ------------------------------------------------------------------

    public JSONObject sharePost(long postId, String platform) throws ApiException, ApiOfflineException {
        return postJson("/posts/" + postId + "/share", new JSONObject().put("platform", platform));
    }

    public JSONObject shareReply(long replyId, String platform) throws ApiException, ApiOfflineException {
        return postJson("/replies/" + replyId + "/share", new JSONObject().put("platform", platform));
    }


    // ------------------------------------------------------------------
    // 5.10 Notifications
    // ------------------------------------------------------------------

    public JSONObject listNotifications() throws ApiException, ApiOfflineException {
        return getJson("/notifications");
    }

    public int unreadNotificationCount() throws ApiException, ApiOfflineException {
        return getJson("/notifications/unread-count").optInt("unread_count", 0);
    }

    public JSONObject markNotificationRead(long notificationId) throws ApiException, ApiOfflineException {
        return patchJson("/notifications/" + notificationId + "/read", new JSONObject());
    }

    public JSONObject markAllNotificationsRead() throws ApiException, ApiOfflineException {
        return patchJson("/notifications/read-all", new JSONObject());
    }

    // ------------------------------------------------------------------
    // Low-level HTTP plumbing
    // ------------------------------------------------------------------

    private JSONObject getJson(String path) throws ApiException, ApiOfflineException {
        return new JSONObject(request("GET", path, null));
    }

    private JSONArray getJsonArray(String path) throws ApiException, ApiOfflineException {
        String raw = request("GET", path, null);
        // Some list endpoints return a bare array, others a Laravel
        // paginator object with a "data" array - handle both so callers
        // don't each need their own guard.
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        JSONObject obj = new JSONObject(trimmed);
        return obj.optJSONArray("data", new JSONArray());
    }

    private JSONObject postJson(String path, JSONObject body) throws ApiException, ApiOfflineException {
        return new JSONObject(request("POST", path, body));
    }

    private JSONObject patchJson(String path, JSONObject body) throws ApiException, ApiOfflineException {
        return new JSONObject(request("PATCH", path, body));
    }

    private void deleteJson(String path) throws ApiException, ApiOfflineException {
        request("DELETE", path, null);
    }

    private String request(String method, String path, JSONObject body) throws ApiException, ApiOfflineException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body.toString());

        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(publisher);
            case "PATCH" -> builder.method("PATCH", publisher);
            case "DELETE" -> builder.DELETE();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ApiOfflineException("Request to " + path + " timed out.", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiOfflineException("Could not reach the server for " + path + ".", e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }

        String message = extractMessage(response.body());
        throw new ApiException(status, message, response.body());
    }

    private static String extractMessage(String body) {
        try {
            JSONObject obj = new JSONObject(body);
            if (obj.has("message")) return obj.getString("message");
            if (obj.has("errors")) return obj.getJSONObject("errors").toString();
        } catch (Exception ignored) {
            // Body wasn't JSON (e.g. an HTML error page) - fall through.
        }
        return body == null || body.isBlank() ? "Request failed." : body;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String multipartPost(String path, String fieldName, File file) throws ApiException, ApiOfflineException {
        String boundary = "----SDFBoundary" + System.currentTimeMillis();
        String fileName = file.getName();
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        if (mimeType == null) mimeType = "application/octet-stream";

        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());

            var parts = new java.util.ArrayList<byte[]>();
            String header = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: " + mimeType + "\r\n\r\n";
            parts.add(header.getBytes(StandardCharsets.UTF_8));
            parts.add(fileBytes);
            parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(parts));

            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            }
            throw new ApiException(status, extractMessage(response.body()), response.body());

        } catch (HttpTimeoutException e) {
            throw new ApiOfflineException("Uploading the avatar timed out.", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiOfflineException("Could not reach the server to upload the avatar.", e);
        }
    }
}
