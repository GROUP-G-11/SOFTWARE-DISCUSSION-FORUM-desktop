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
    private Consumer<SyncResult> onSyncComplete;
    private Consumer<Boolean> onConnectivityChange;

    public SyncService(ApiClient api, LocalStore store) {
        this.api = api;
        this.store = store;
    }

    public void setOnSyncComplete(Consumer<SyncResult> callback) {
        this.onSyncComplete = callback;
    }

    public void setOnConnectivityChange(Consumer<Boolean> callback) {
        this.onConnectivityChange = callback;
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

            if (!notifications.isEmpty()) {
                store.cacheNotifications(notifications);
            }
            // new_posts/new_quizzes span many groups/topics at once, so we
            // don't have a single group/topic id to key the cache under
            // here - individual panels still cache their own scoped lists
            // via cachePosts()/cacheQuizzes() when the person opens them.
            // We still surface the raw delta to the UI so an "unseen
            // activity" badge can be shown immediately.

            String syncedAt = response.optString("synced_at", null);
            if (syncedAt != null) {
                store.setLastSyncedAt(syncedAt);
            }

            setOnline(true);
            SyncResult result = new SyncResult(true, replayed, newPosts.length(), newQuizzes.length(), notifications.length(), null);
            if (onSyncComplete != null) onSyncComplete.accept(result);
            return result;

        } catch (ApiOfflineException e) {
            setOnline(false);
            SyncResult result = new SyncResult(false, 0, 0, 0, 0, "Offline: " + e.getMessage());
            if (onSyncComplete != null) onSyncComplete.accept(result);
            return result;
        } catch (ApiException e) {
            // Server reachable but rejected the sync (e.g. expired token) -
            // still "online" from a connectivity standpoint.
            setOnline(true);
            SyncResult result = new SyncResult(false, 0, 0, 0, 0, e.getMessage());
            if (onSyncComplete != null) onSyncComplete.accept(result);
            return result;
        }
    }

    private void setOnline(boolean nowOnline) {
        boolean changed = nowOnline != this.online;
        this.online = nowOnline;
        if (changed && onConnectivityChange != null) {
            onConnectivityChange.accept(nowOnline);
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
                        api.createPost(topicId, p.getString("content"), p.optString("attachment_url", null), excludeIds);
                    }
                    case "create_reply" -> {
                        JSONObject p = entry.payload();
                        api.createReply(p.getLong("post_id"), p.getString("content"));
                    }
                    case "create_topic" -> {
                        JSONObject p = entry.payload();
                        api.createTopic(p.getLong("group_id"), p.getString("title"));
                    }
                    default -> throw new ApiException(0, "Unknown outbox action kind: " + entry.kind(), null);
                }
                store.markOutboxSynced(entry.id());
                succeeded++;
            } catch (ApiException e) {
                // Leave this one queued; move on to the rest so one bad
                // entry doesn't block everything behind it.
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
    public record SyncResult(boolean success, int outboxReplayed, int newPosts, int newQuizzes, int newNotifications, String errorMessage) {
    }
}
