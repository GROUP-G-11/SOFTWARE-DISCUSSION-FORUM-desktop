package com.smartforum.desktop.db;

import com.smartforum.desktop.util.AppConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The desktop client's offline store (SDD 4.2 "SyncRecord" table, and the
 * "Local SQLite Database" storage layer described in SDD 3.1 / 4.1).
 *
 * Two jobs:
 *   1. Cache the last-known copy of anything the person has viewed, keyed
 *      by its server ID, so it's still readable with no network.
 *   2. Queue up actions performed while offline (a post composed with no
 *      connection, for example) in the {@code outbox} table, so
 *      {@link com.smartforum.desktop.sync.SyncService} can replay them once
 *      connectivity returns.
 *
 * Every cached row keeps the full server JSON alongside a few indexed
 * columns, so callers can either query structured columns for lists/search,
 * or re-parse the JSON for full detail - the same shape the API itself
 * would have returned.
 *
 * All public (and DB-touching private) methods below are `synchronized`.
 * This class is backed by a SINGLE shared JDBC Connection, but it's called
 * from many different background threads at once - refreshThread(),
 * refreshTopicList(), sendPost(), quickReply(), CreateTopicDialog.submit(),
 * and SyncService's own poll cycle can all be mid-flight simultaneously.
 * The SQLite JDBC driver does not guarantee safety when one Connection is
 * driven concurrently by multiple threads, so without this locking you get
 * exactly the symptoms this used to cause: scrambled row order, rows that
 * silently vanish or reappear, and reads that only pick up whichever
 * thread's writes landed first. Synchronizing serializes all local-DB
 * access across threads and removes the race entirely.
 */
public class LocalStore implements AutoCloseable {

    private final Connection connection;

    /** After this many failed replay attempts, a queued action stops retrying and is surfaced to the user as permanently failed instead of silently retried forever. */
    private static final int MAX_OUTBOX_RETRIES = 5;

    public LocalStore() {
        this(AppConfig.dbFile());
    }

    public LocalStore(String dbFilePath) {
        try {
            File file = new File(dbFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            initSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open local SQLite cache at " + dbFilePath, e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS sync_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS cached_groups (
                    group_id INTEGER PRIMARY KEY,
                    name TEXT,
                    description TEXT,
                    member_count INTEGER,
                    topic_count INTEGER,
                    is_member INTEGER,
                    is_group_admin INTEGER,
                    raw_json TEXT,
                    cached_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS cached_topics (
                    topic_id INTEGER PRIMARY KEY,
                    group_id INTEGER,
                    title TEXT,
                    category TEXT,
                    posts_count INTEGER,
                    raw_json TEXT,
                    cached_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS cached_posts (
                    post_id INTEGER PRIMARY KEY,
                    topic_id INTEGER,
                    author_id INTEGER,
                    author_name TEXT,
                    content TEXT,
                    posted_at TEXT,
                    raw_json TEXT,
                    cached_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS cached_quizzes (
                    quiz_id INTEGER PRIMARY KEY,
                    group_id INTEGER,
                    title TEXT,
                    status TEXT,
                    raw_json TEXT,
                    cached_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS cached_notifications (
                    notification_id INTEGER PRIMARY KEY,
                    message TEXT,
                    is_read INTEGER,
                    created_at TEXT,
                    raw_json TEXT,
                    cached_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Actions composed while offline, waiting to be replayed once
            // the server is reachable again (SDD Table 34: Synchronize
            // Messages use case, steps 2-3). "kind" identifies which
            // ApiClient call to replay; "payload" is that call's JSON body.
            st.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    synced INTEGER DEFAULT 0,
                    failed INTEGER DEFAULT 0,
                    error_message TEXT
                )
            """);
            addColumnIfMissing(st, "outbox", "failed", "INTEGER DEFAULT 0");
            addColumnIfMissing(st, "outbox", "error_message", "TEXT");
            addColumnIfMissing(st, "outbox", "retry_count", "INTEGER DEFAULT 0");

            // Files (e.g. exported topic PDFs) saved for offline access -
            // SDD 4.1 "File Storage" layer, mirrored locally so a previously
            // exported thread can still be opened with no connection.
            st.execute("""
                CREATE TABLE IF NOT EXISTS cached_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind TEXT,
                    reference_id INTEGER,
                    file_name TEXT,
                    local_path TEXT,
                    saved_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    private void addColumnIfMissing(Statement st, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    // ------------------------------------------------------------------
    // sync_meta (mirrors the server's SyncRecord: last_synced_at per device)
    // ------------------------------------------------------------------

    public synchronized void setMeta(String key, String value) {
        String sql = "INSERT INTO sync_meta(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to write sync_meta[" + key + "]", e);
        }
    }

    public synchronized String getMeta(String key) {
        String sql = "SELECT value FROM sync_meta WHERE key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read sync_meta[" + key + "]", e);
        }
    }

    public synchronized String getLastSyncedAt() {
        return getMeta("last_synced_at");
    }

    public synchronized void setLastSyncedAt(String isoTimestamp) {
        setMeta("last_synced_at", isoTimestamp);
    }

    // ------------------------------------------------------------------
    // Caching groups/topics/posts/quizzes/notifications
    // ------------------------------------------------------------------

    public synchronized void cacheGroups(JSONArray groups) {
        String sql = """
            INSERT INTO cached_groups(group_id, name, description, member_count, topic_count, is_member, is_group_admin, raw_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(group_id) DO UPDATE SET
                name=excluded.name, description=excluded.description, member_count=excluded.member_count,
                topic_count=excluded.topic_count, is_member=excluded.is_member, is_group_admin=excluded.is_group_admin,
                raw_json=excluded.raw_json, cached_at=CURRENT_TIMESTAMP
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.getJSONObject(i);
                ps.setLong(1, g.getLong("group_id"));
                ps.setString(2, g.optString("name", ""));
                ps.setString(3, g.optString("description", ""));
                ps.setInt(4, g.optInt("members_count", 0));
                ps.setInt(5, g.optInt("topics_count", 0));
                ps.setInt(6, g.optBoolean("is_member", false) ? 1 : 0);
                ps.setInt(7, g.optBoolean("is_group_admin", false) ? 1 : 0);
                ps.setString(8, g.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cache groups", e);
        }
    }

    public synchronized List<JSONObject> cachedGroups() {
        return queryAllRawJson("SELECT raw_json FROM cached_groups ORDER BY name");
    }

    public synchronized void cacheTopics(long groupId, JSONArray topics) {
        String sql = """
            INSERT INTO cached_topics(topic_id, group_id, title, category, posts_count, raw_json)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(topic_id) DO UPDATE SET
                title=excluded.title, category=excluded.category, posts_count=excluded.posts_count,
                raw_json=excluded.raw_json, cached_at=CURRENT_TIMESTAMP
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < topics.length(); i++) {
                JSONObject t = topics.getJSONObject(i);
                ps.setLong(1, t.getLong("topic_id"));
                ps.setLong(2, groupId);
                ps.setString(3, t.optString("title", ""));
                ps.setString(4, t.optString("category", ""));
                ps.setInt(5, t.optInt("posts_count", 0));
                ps.setString(6, t.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cache topics for group " + groupId, e);
        }
    }

    public synchronized List<JSONObject> cachedTopics(long groupId) {
        return queryAllRawJson("SELECT raw_json FROM cached_topics WHERE group_id = " + groupId + " ORDER BY topic_id DESC");
    }

    public synchronized void cachePosts(long topicId, JSONArray posts) {
        String sql = """
            INSERT INTO cached_posts(post_id, topic_id, author_id, author_name, content, posted_at, raw_json)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(post_id) DO UPDATE SET
                content=excluded.content, posted_at=excluded.posted_at, raw_json=excluded.raw_json, cached_at=CURRENT_TIMESTAMP
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < posts.length(); i++) {
                JSONObject p = posts.getJSONObject(i);
                JSONObject author = p.optJSONObject("author");
                ps.setLong(1, p.getLong("post_id"));
                ps.setLong(2, topicId);
                ps.setLong(3, author != null ? author.optLong("user_id", 0) : 0);
                ps.setString(4, author != null ? author.optString("full_name", "Unknown") : "Unknown");
                ps.setString(5, p.optString("content", ""));
                ps.setString(6, p.optString("posted_at", ""));
                ps.setString(7, p.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cache posts for topic " + topicId, e);
        }
    }

    public synchronized void cachePostsByTopic(JSONArray posts) {
        Map<Long, JSONArray> byTopic = new HashMap<>();
        for (int i = 0; i < posts.length(); i++) {
            JSONObject post = posts.getJSONObject(i);
            long topicId = post.optLong("topic_id", -1);
            if (topicId > 0) {
                byTopic.computeIfAbsent(topicId, ignored -> new JSONArray()).put(post);
            }
        }
        byTopic.forEach(this::cachePosts);
    }

    public synchronized void cachePendingTopic(long groupId, long outboxId, JSONObject payload, JSONObject user) {
        JSONObject topic = new JSONObject()
                .put("topic_id", -outboxId)
                .put("group_id", groupId)
                .put("title", payload.optString("title", ""))
                .put("category", JSONObject.NULL)
                .put("posts_count", 0)
                .put("author", cachedAuthor(user))
                .put("sync_status", "pending")
                .put("outbox_id", outboxId);
        cacheTopics(groupId, new JSONArray().put(topic));
    }

    public synchronized void removeLocalPendingTopic(long groupId, long outboxId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cached_topics WHERE topic_id = ?")) {
            ps.setLong(1, -outboxId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove pending topic " + outboxId, e);
        }
    }

    public synchronized void markLocalPendingTopicFailed(long groupId, long outboxId, String message) {
        String sql = "SELECT raw_json FROM cached_topics WHERE topic_id = ?";
        JSONObject topic = null;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, -outboxId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) topic = new JSONObject(rs.getString("raw_json"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read pending topic " + outboxId, e);
        }
        if (topic == null) return;
        topic.put("sync_status", "failed");
        topic.put("sync_error", message);
        cacheTopics(groupId, new JSONArray().put(topic));
    }
    public synchronized void cachePendingPost(long topicId, long outboxId, JSONObject payload, JSONObject user) {
        JSONObject author = cachedAuthor(user);
        JSONObject post = new JSONObject()
                .put("post_id", -outboxId)
                .put("topic_id", topicId)
                .put("author", author)
                .put("content", payload.optString("content", ""))
                .put("posted_at", "Pending sync")
                .put("replies", new JSONArray())
                .put("sync_status", "pending")
                .put("outbox_id", outboxId);
        cachePosts(topicId, new JSONArray().put(post));
    }

    public synchronized void cachePendingReply(long postId, long outboxId, JSONObject payload, JSONObject user) {
        JSONObject post = cachedPost(postId);
        if (post == null) return;

        JSONArray replies = post.optJSONArray("replies");
        if (replies == null) {
            replies = new JSONArray();
            post.put("replies", replies);
        }
        replies.put(new JSONObject()
                .put("reply_id", -outboxId)
                .put("post_id", postId)
                .put("author", cachedAuthor(user))
                .put("content", payload.optString("content", ""))
                .put("replied_at", "Pending sync")
                .put("sync_status", "pending")
                .put("outbox_id", outboxId));
        cachePosts(post.optLong("topic_id", -1), new JSONArray().put(post));
    }

    public synchronized void removeLocalPendingPost(long outboxId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cached_posts WHERE post_id = ?")) {
            ps.setLong(1, -outboxId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove pending post " + outboxId, e);
        }
    }

    public synchronized void removeLocalPendingReply(long postId, long outboxId) {
        JSONObject post = cachedPost(postId);
        if (post == null) return;
        JSONArray replies = post.optJSONArray("replies");
        if (replies == null) return;

        JSONArray kept = new JSONArray();
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.getJSONObject(i);
            if (reply.optLong("outbox_id", Long.MIN_VALUE) != outboxId) {
                kept.put(reply);
            }
        }
        post.put("replies", kept);
        cachePosts(post.optLong("topic_id", -1), new JSONArray().put(post));
    }

    public synchronized void markLocalPendingFailed(OutboxEntry entry, String message) {
        JSONObject payload = entry.payload();
        if ("create_topic".equals(entry.kind())) {
            markLocalPendingTopicFailed(payload.optLong("group_id", -1), entry.id(), message);
        } else if ("create_post".equals(entry.kind())) {
            JSONObject post = cachedPost(-entry.id());
            if (post != null) {
                post.put("sync_status", "failed");
                post.put("sync_error", message);
                post.put("posted_at", "Failed to sync");
                cachePosts(post.optLong("topic_id", payload.optLong("topic_id", -1)), new JSONArray().put(post));
            }
        } else if ("create_reply".equals(entry.kind())) {
            long postId = payload.optLong("post_id", -1);
            JSONObject post = cachedPost(postId);
            if (post == null) return;
            JSONArray replies = post.optJSONArray("replies");
            if (replies == null) return;
            for (int i = 0; i < replies.length(); i++) {
                JSONObject reply = replies.getJSONObject(i);
                if (reply.optLong("outbox_id", Long.MIN_VALUE) == entry.id()) {
                    reply.put("sync_status", "failed");
                    reply.put("sync_error", message);
                    reply.put("replied_at", "Failed to sync");
                }
            }
            cachePosts(post.optLong("topic_id", -1), new JSONArray().put(post));
        }
    }

    private synchronized JSONObject cachedPost(long postId) {
        String sql = "SELECT raw_json FROM cached_posts WHERE post_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new JSONObject(rs.getString("raw_json")) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read cached post " + postId, e);
        }
    }

    private JSONObject cachedAuthor(JSONObject user) {
        JSONObject author = new JSONObject()
                .put("user_id", user == null ? 0 : user.optLong("user_id", 0))
                .put("full_name", user == null ? "You" : user.optString("full_name", "You"));
        return author.put("cached_at", Instant.now().toString());
    }

    public synchronized List<JSONObject> cachedPosts(long topicId) {

        return queryAllRawJson(
                "SELECT raw_json FROM cached_posts WHERE topic_id = " + topicId +
                        " ORDER BY (post_id < 0) ASC, ABS(post_id) ASC"
        );
    }

    public synchronized void cacheQuizzes(long groupId, JSONArray quizzes) {
        String sql = """
            INSERT INTO cached_quizzes(quiz_id, group_id, title, status, raw_json)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(quiz_id) DO UPDATE SET
                title=excluded.title, status=excluded.status, raw_json=excluded.raw_json, cached_at=CURRENT_TIMESTAMP
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < quizzes.length(); i++) {
                JSONObject q = quizzes.getJSONObject(i);
                ps.setLong(1, q.getLong("quiz_id"));
                ps.setLong(2, groupId);
                ps.setString(3, q.optString("title", ""));
                ps.setString(4, q.optString("status", ""));
                ps.setString(5, q.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cache quizzes for group " + groupId, e);
        }
    }

    public synchronized List<JSONObject> cachedQuizzes(long groupId) {
        return queryAllRawJson("SELECT raw_json FROM cached_quizzes WHERE group_id = " + groupId + " ORDER BY quiz_id DESC");
    }

    public synchronized void cacheNotifications(JSONArray notifications) {
        String sql = """
            INSERT INTO cached_notifications(notification_id, message, is_read, created_at, raw_json)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(notification_id) DO UPDATE SET
                message=excluded.message, is_read=excluded.is_read, raw_json=excluded.raw_json, cached_at=CURRENT_TIMESTAMP
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < notifications.length(); i++) {
                JSONObject n = notifications.getJSONObject(i);
                ps.setLong(1, n.getLong("notification_id"));
                ps.setString(2, n.optString("message", ""));
                ps.setInt(3, n.optBoolean("is_read", false) ? 1 : 0);
                ps.setString(4, n.optString("created_at", ""));
                ps.setString(5, n.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cache notifications", e);
        }
    }

    public synchronized List<JSONObject> cachedNotifications() {
        return queryAllRawJson("SELECT raw_json FROM cached_notifications ORDER BY notification_id DESC");
    }

    // ------------------------------------------------------------------
    // Outbox (offline-composed actions waiting to sync)
    // ------------------------------------------------------------------

    /** Queue an action performed while offline. `kind` identifies what it was (e.g. "create_post"), `payload` is the JSON ApiClient would have sent. */
    public synchronized long queueOutboxAction(String kind, JSONObject payload) {
        String sql = "INSERT INTO outbox(kind, payload) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, kind);
            ps.setString(2, payload.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to queue outbox action: " + kind, e);
        }
    }

    public synchronized List<OutboxEntry> pendingOutboxActions() {
        List<OutboxEntry> out = new ArrayList<>();
        String sql = "SELECT id, kind, payload, created_at, error_message, retry_count FROM outbox WHERE synced = 0 AND failed = 0 ORDER BY id ASC";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new OutboxEntry(
                        rs.getLong("id"),
                        rs.getString("kind"),
                        new JSONObject(rs.getString("payload")),
                        rs.getString("created_at"),
                        rs.getString("error_message"),
                        rs.getInt("retry_count")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read pending outbox actions", e);
        }
        return out;
    }

    public synchronized void markOutboxSynced(long outboxId) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE outbox SET synced = 1, failed = 0, error_message = NULL WHERE id = ?")) {
            ps.setLong(1, outboxId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark outbox entry " + outboxId + " synced", e);
        }
    }

    public synchronized void markOutboxFailed(long outboxId, String errorMessage) {
        // Retries a handful of times (e.g. a transient server hiccup) before
        // giving up - previously this set failed=1 on the very first error,
        // so one rejected action stayed stuck forever, since
        // pendingOutboxActions() never looks at failed=1 rows again, which
        // permanently inflated the "N item(s) need attention" count.
        String sql = "UPDATE outbox SET retry_count = retry_count + 1, " +
                "failed = CASE WHEN retry_count + 1 >= ? THEN 1 ELSE 0 END, " +
                "error_message = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, MAX_OUTBOX_RETRIES);
            ps.setString(2, errorMessage);
            ps.setLong(3, outboxId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark outbox entry " + outboxId + " failed", e);
        }
    }

    public synchronized int pendingOutboxCount() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM outbox WHERE synced = 0 AND failed = 0")) {
            return rs.next() ? rs.getInt("c") : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count pending outbox actions", e);
        }
    }

    public synchronized int failedOutboxCount() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM outbox WHERE synced = 0 AND failed = 1")) {
            return rs.next() ? rs.getInt("c") : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count failed outbox actions", e);
        }
    }

    /** Drops every entry that used up all its retries, so the "N item(s) need attention" count can actually be cleared instead of growing forever. */
    public synchronized int clearFailedOutbox() {
        try (Statement st = connection.createStatement()) {
            return st.executeUpdate("DELETE FROM outbox WHERE failed = 1");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear failed outbox entries", e);
        }
    }

    // ------------------------------------------------------------------
    // Cached files (offline-readable exports, e.g. topic PDFs)
    // ------------------------------------------------------------------

    public synchronized void recordCachedFile(String kind, long referenceId, String fileName, String localPath) {
        String sql = "INSERT INTO cached_files(kind, reference_id, file_name, local_path) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, kind);
            ps.setLong(2, referenceId);
            ps.setString(3, fileName);
            ps.setString(4, localPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record cached file", e);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private synchronized List<JSONObject> queryAllRawJson(String sql) {
        List<JSONObject> out = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new JSONObject(rs.getString("raw_json")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
        return out;
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}