package com.smartforum.desktop.sync;

import com.smartforum.desktop.api.ApiClient;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import com.smartforum.desktop.db.LocalStore;
import com.smartforum.desktop.db.OutboxEntry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Synchronize Messages use case (SDD Table 34). On every sync cycle:
 *   1. Replay anything queued in the outbox while offline (create post,
 *      create reply, create topic - the actions a person can compose
 *      without a connection).
 *   2. Call POST /sync with this device's last_synced_at watermark.
 *   3. Cache whatever came back (new posts, new quizzes, notifications)
 *      and advance the watermark to the timestamp the server returned.
 *
 * A background poll keeps this running automatically (mirrors the web
 * client's poll loop against the same /sync endpoint), and {@link #syncNow}
 * lets any panel force an immediate sync (e.g. right after regaining
 * connectivity, or on manual refresh).
 */
public class SyncService {

    private final ApiClient api;
    private final LocalStore store;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sdf-sync");
        t.setDaemon(true);
        return t;
    });
    private java.util.concurrent.ScheduledFuture<?> pollingTask;

    private volatile boolean online = true;
    // Multiple parts of the UI need to react to a sync cycle finishing (the
    // sidebar status line, but also whatever topic thread happens to be open
    // right now) so these are lists rather than a single overwritable slot -
    // registering a second listener no longer silently drops the first one.
    private final List<Consumer<SyncResult>> onSyncComplete = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Consumer<Boolean>> onConnectivityChange = new java.util.concurrent.CopyOnWriteArrayList<>();

    public SyncService(ApiClient api, LocalStore store) {
        this.api = api;
        this.store = store;
    }

    /** Registers a callback to run after every sync cycle. Can be called more than once; every registered callback runs. */
    public void setOnSyncComplete(Consumer<SyncResult> callback) {
        this.onSyncComplete.add(callback);
    }

    /** Registers a callback to run whenever online/offline status flips. Can be called more than once; every registered callback runs. */
    public void setOnConnectivityChange(Consumer<Boolean> callback) {
        this.onConnectivityChange.add(callback);
    }

    public boolean isOnline() {
        return online;
    }

    /** Starts periodic background sync (every {@code intervalSeconds}). */
    public void startPolling(int intervalSeconds) {
        if (pollingTask != null) {
            pollingTask.cancel(false);
        }
        pollingTask = scheduler.scheduleWithFixedDelay(this::syncNow, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
    }

    /** Runs one sync cycle immediately, off the calling thread if it's the Swing EDT would be a mistake - callers should invoke this from a background thread (e.g. via SwingWorker). */
    public synchronized SyncResult syncNow() {
        try {
            int replayed = replayOutbox();
            JSONObject response = api.sync(store.getLastSyncedAt(), new JSONArray());

            JSONArray newPosts = response.optJSONArray("new_posts", new JSONArray());
            JSONArray newQuizzes = response.optJSONArray("new_quizzes", new JSONArray());
            JSONArray notifications = response.optJSONArray("notifications", new JSONArray());

            if (!newPosts.isEmpty()) {
                store.cachePostsByTopic(newPosts);
            }
            if (!notifications.isEmpty()) {
                store.cacheNotifications(notifications);
            }
            // Quizzes can span many groups and the sync payload does not
            // guarantee a group_id for every row, so scoped quiz panels still
            // refresh their own cache when opened.

            String syncedAt = response.optString("synced_at", null);
            if (syncedAt != null) {
                store.setLastSyncedAt(syncedAt);
            }

            setOnline(true);
            SyncResult result = new SyncResult(true, replayed, newPosts.length(), newQuizzes.length(), notifications.length(), store.failedOutboxCount(), null);
            onSyncComplete.forEach(l -> l.accept(result));
            return result;

        } catch (ApiOfflineException e) {
            setOnline(false);
            SyncResult result = new SyncResult(false, 0, 0, 0, 0, store.failedOutboxCount(), "Offline: " + e.getMessage());
            onSyncComplete.forEach(l -> l.accept(result));
            return result;
        } catch (ApiException e) {
            // Server reachable but rejected the sync (e.g. expired token) -
            // still "online" from a connectivity standpoint.
            setOnline(true);
            SyncResult result = new SyncResult(false, 0, 0, 0, 0, store.failedOutboxCount(), e.getMessage());
            onSyncComplete.forEach(l -> l.accept(result));
            return result;
        } catch (RuntimeException e) {
            // IMPORTANT: this task is driven by scheduleWithFixedDelay(), and
            // that scheduler PERMANENTLY stops running this task if any
            // execution throws an exception it doesn't catch. A malformed
            // outbox payload (JSONException) or a local SQLite hiccup
            // (IllegalStateException from LocalStore) used to escape all the
            // way out of syncNow() and silently kill background sync for the
            // rest of the session - this is what caused sync to just stop
            // working with no visible error. Catch it, report it, keep polling.
            SyncResult result = new SyncResult(false, 0, 0, 0, 0, store.failedOutboxCount(), "Sync error: " + e.getMessage());
            onSyncComplete.forEach(l -> l.accept(result));
            return result;
        }
    }

    private void setOnline(boolean nowOnline) {
        boolean changed = nowOnline != this.online;
        this.online = nowOnline;
        if (changed) {
            onConnectivityChange.forEach(l -> l.accept(nowOnline));
        }
    }

    /**
     * Replays every pending outbox entry against the real endpoint it
     * represents. Each entry is only marked synced once the server
     * confirms it - if the server itself is reachable but rejects a
     * specific action (e.g. blacklisted since it was queued), that one
     * entry is left in the outbox and surfaced as a failure rather than
     * silently dropped or endlessly retried on every cycle.
     */
    private int replayOutbox() throws ApiOfflineException {
        List<OutboxEntry> pending = store.pendingOutboxActions();
        int succeeded = 0;
        for (OutboxEntry entry : pending) {
            try {
                switch (entry.kind()) {
                    case "create_post" -> {
                        JSONObject p = entry.payload();
                        long topicId = p.getLong("topic_id");
                        long[] excludeIds = toLongArray(p.optJSONArray("exclude_user_ids"));
                        // client_ref was stamped onto the payload when the action was first
                        // composed (see TopicWorkspacePanel#sendPost), so a retry after a
                        // timeout - or a replay of the same outbox row on the next poll -
                        // always sends the SAME ref. A server that enforces uniqueness on
                        // (user, client_ref) can return the original post instead of
                        // creating a second one, which is what was causing sent-while-offline
                        // messages to duplicate on sync.
                        JSONObject created = api.createPost(topicId, p.getString("content"), p.optString("attachment_url", null), excludeIds, p.optString("client_ref", null));
                        if (!created.has("topic_id")) {
                            created.put("topic_id", topicId);
                        }
                        store.cachePosts(topicId, new JSONArray().put(created));
                        store.removeLocalPendingPost(entry.id());
                    }
                    case "create_reply" -> {
                        JSONObject p = entry.payload();
                        api.createReply(p.getLong("post_id"), p.getString("content"), p.optString("client_ref", null));
                        store.removeLocalPendingReply(p.getLong("post_id"), entry.id());
                    }
                    case "create_topic" -> {
                        JSONObject p = entry.payload();
                        long groupId = p.getLong("group_id");
                        JSONObject created = api.createTopic(groupId, p.getString("title"), p.optString("client_ref", null));
                        store.cacheTopics(groupId, new JSONArray().put(created));
                        store.removeLocalPendingTopic(groupId, entry.id());
                    }
                    default -> throw new ApiException(0, "Unknown outbox action kind: " + entry.kind(), null);
                }
                store.markOutboxSynced(entry.id());
                succeeded++;
            } catch (ApiException e) {
                store.markOutboxFailed(entry.id(), e.getMessage());
                store.markLocalPendingFailed(entry, e.getMessage());
            } catch (RuntimeException e) {
                // A malformed payload (JSONException from p.getLong/getString)
                // or a local DB error (IllegalStateException) used to escape
                // this loop entirely, aborting replay of every remaining
                // outbox entry and bubbling up to kill the whole poll cycle.
                // Treat it the same as a rejected action instead: fail just
                // this one entry and keep going.
                store.markOutboxFailed(entry.id(), "Client error: " + e.getMessage());
                store.markLocalPendingFailed(entry, e.getMessage());
            }
        }
        return succeeded;
    }

    private static long[] toLongArray(JSONArray arr) {
        if (arr == null) return new long[0];
        long[] out = new long[arr.length()];
        for (int i = 0; i < arr.length(); i++) out[i] = arr.getLong(i);
        return out;
    }

    /** Outcome of one sync cycle, for the UI to show a status line / toast. */
    public record SyncResult(boolean success, int outboxReplayed, int newPosts, int newQuizzes, int newNotifications, int failedOutbox, String errorMessage) {
    }
}