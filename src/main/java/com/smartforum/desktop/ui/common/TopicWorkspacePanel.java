package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.List;

public class TopicWorkspacePanel extends JPanel {

    private final AppContext ctx;
    private final CardLayout inner = new CardLayout();
    private final JPanel innerHost = new JPanel(inner);
    private final JPanel topicListBody = new JPanel();
    private final JPanel threadBody = new JPanel();
    private final JLabel threadTitle = new JLabel();
    private final JLabel groupTitle = new JLabel("Topics");
    private final JTextField searchField = new JTextField();
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(350, null);

    private long currentGroupId = -1;
    private long currentTopicId = -1;
    private String currentGroupName = "Group";
    private String currentView = "list";

    public TopicWorkspacePanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout());
        setBackground(Theme.WHITE);
        add(innerHost, BorderLayout.CENTER);

        innerHost.add(buildTopicListView(), "list");
        innerHost.add(buildThreadView(), "thread");

        // A background sync cycle can bring in new posts (or resolve queued
        // topics/posts/replies) for whatever this panel happens to be
        // showing right now. Without this, "sync succeeded" and "what's on
        // screen" can drift apart until the user manually navigates away
        // and back - refresh the visible view whenever that happens.
        ctx.sync.setOnSyncComplete(result -> {
            if (!result.success()) return;
            SwingUtilities.invokeLater(() -> {
                if ("thread".equals(currentView) && currentTopicId > 0) {
                    refreshThread();
                } else if ("list".equals(currentView) && currentGroupId > 0) {
                    refreshTopicList();
                }
            });
        });
    }

    public void openGroup(long groupId, String groupName) {
        this.currentGroupId = groupId;
        this.currentGroupName = groupName == null ? "Group" : groupName;
        groupTitle.setText(groupName == null ? "Topics" : groupName + " \u2014 Topics");
        this.currentView = "list";
        inner.show(innerHost, "list");
        refreshTopicList();
    }

    // ------------------------------------------------------------------
    // Topic list view
    // ------------------------------------------------------------------

    private JComponent buildTopicListView() {
        JPanel wrap = new JPanel(new BorderLayout(0, 12));
        wrap.setBorder(new EmptyBorder(24, 28, 24, 28));
        wrap.setBackground(Theme.WHITE);

        groupTitle.setFont(Theme.HEADING_FONT);
        groupTitle.setForeground(Theme.INK);

        JButton membersBtn = Buttons.secondary("Members");
        membersBtn.addActionListener(e ->
                new GroupMembersDialog(SwingUtilities.getWindowAncestor(this), ctx, currentGroupId, currentGroupName).setVisible(true));

        JButton newTopicBtn = Buttons.primary("+ New Topic");
        newTopicBtn.addActionListener(e -> createTopic());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(groupTitle, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(membersBtn);
        actions.add(newTopicBtn);
        top.add(actions, BorderLayout.EAST);

        topicListBody.setLayout(new BoxLayout(topicListBody, BoxLayout.Y_AXIS));
        topicListBody.setOpaque(false);

        JPanel headerStack = new JPanel();
        headerStack.setLayout(new BoxLayout(headerStack, BoxLayout.Y_AXIS));
        headerStack.setOpaque(false);
        headerStack.add(top);
        headerStack.add(Box.createVerticalStrut(14));
        headerStack.add(buildSearchBar());

        wrap.add(headerStack, BorderLayout.NORTH);
        wrap.add(new JScrollPane(topicListBody), BorderLayout.CENTER);
        return wrap;
    }

    private void refreshTopicList() {
        long groupId = currentGroupId;
        new SwingWorker<JSONArray, Void>() {
            boolean fromCache = false;

            @Override
            protected JSONArray doInBackground() {
                try {
                    String q = searchField.getText();
                    String search = (q == null || q.equals("Search topics...") || q.isBlank()) ? null : q.trim();
                    JSONObject response = ctx.api.listTopics(groupId, search, null);
                    JSONArray data = response.optJSONArray("data", new JSONArray());
                    ctx.store.cacheTopics(groupId, data);
                    return data;
                } catch (ApiOfflineException | ApiException e) {
                    fromCache = true;
                    JSONArray arr = new JSONArray();
                    ctx.store.cachedTopics(groupId).forEach(arr::put);
                    return arr;
                }
            }

            @Override
            protected void done() {
                try {
                    renderTopicList(get(), fromCache);
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void renderTopicList(JSONArray topics, boolean fromCache) {
        topicListBody.removeAll();
        if (fromCache) {
            JLabel notice = new JLabel("Offline \u2013 showing topics last saved to this device.");
            notice.setForeground(Theme.WARN);
            notice.setFont(Theme.SMALL_FONT);
            topicListBody.add(notice);
            topicListBody.add(Box.createVerticalStrut(8));
        }
        for (int i = 0; i < topics.length(); i++) {
            JSONObject t = topics.getJSONObject(i);
            topicListBody.add(topicRow(t));
            topicListBody.add(Box.createVerticalStrut(1));
        }
        topicListBody.revalidate();
        topicListBody.repaint();
    }

    private JComponent topicRow(JSONObject topic) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(12, 4, 12, 4)));
        row.setBackground(Theme.WHITE);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        String syncStatus = topic.optString("sync_status", "");
        JLabel title = new JLabel(topic.optString("title", "Untitled topic") + ("pending".equals(syncStatus) ? "  (sending\u2026)" : "failed".equals(syncStatus) ? "  (failed to send)" : ""));
        title.setFont(Theme.BODY_FONT_BOLD.deriveFont(14f));
        String metaText = "failed".equals(syncStatus)
                ? "Will retry once you're back online" + (topic.optString("sync_error", "").isBlank() ? "" : ": " + topic.optString("sync_error", ""))
                : topic.optInt("posts_count", 0) + " replies" + (topic.has("category") && !topic.isNull("category") ? "  \u00b7  " + topic.optString("category") : "");
        JLabel meta = new JLabel(metaText);
        meta.setFont(Theme.SMALL_FONT);
        meta.setForeground("failed".equals(syncStatus) ? Theme.WARN : Color.GRAY);
        left.add(title);
        left.add(meta);

        row.add(left, BorderLayout.WEST);

        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openTopic(topic.getLong("topic_id"), topic.optString("title", "Topic"));
            }
        });
        return row;
    }

    private void createTopic() {
        new CreateTopicDialog(SwingUtilities.getWindowAncestor(this), ctx, currentGroupId,
                success -> refreshTopicList()).setVisible(true);
    }



    // ------------------------------------------------------------------
    // Thread (posts + replies) view
    // ------------------------------------------------------------------

    private final JPanel postsBody = new JPanel();
    private final JScrollPane postsScroll = new JScrollPane(postsBody);
    private final JTextArea composer = new JTextArea(1, 30);
    private final JButton excludeBtn = Buttons.secondary("Exclude members");
    private final JButton exportBtn = Buttons.secondary("Export PDF");
    private final JButton sendBtn = Buttons.primary("Send");
    private final java.util.Set<Long> excludedUserIds = new java.util.HashSet<>();

    private JComponent buildThreadView() {
        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setBorder(new EmptyBorder(24, 28, 24, 28));
        wrap.setBackground(Theme.WHITE);

        JButton back = Buttons.secondary("\u2190 Back to topics");
        back.addActionListener(e -> {
            currentView = "list";
            inner.show(innerHost, "list");
        });

        threadTitle.setFont(Theme.HEADING_FONT);
        threadTitle.setForeground(Theme.INK);

        exportBtn.addActionListener(e -> exportPdf());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel topLeft = new JPanel();
        topLeft.setOpaque(false);
        topLeft.setLayout(new BoxLayout(topLeft, BoxLayout.Y_AXIS));
        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backRow.setOpaque(false);
        backRow.add(back);
        topLeft.add(backRow);
        topLeft.add(threadTitle);
        top.add(topLeft, BorderLayout.WEST);
        top.add(exportBtn, BorderLayout.EAST);

        postsBody.setLayout(new BoxLayout(postsBody, BoxLayout.Y_AXIS));
        postsBody.setOpaque(true);
        postsBody.setBackground(Theme.PAPER_DIM);
        postsBody.setBorder(new EmptyBorder(16, 16, 16, 16));
        postsScroll.setBorder(BorderFactory.createEmptyBorder());
        postsScroll.getVerticalScrollBar().setUnitIncrement(16);
        composer.setLineWrap(true);
        composer.setWrapStyleWord(true);
        composer.setFont(Theme.BODY_FONT);
        composer.setRows(1);
        composer.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane composerScroll = new JScrollPane(composer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        composerScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE, 1, true));
        composerScroll.setPreferredSize(new Dimension(400, 38));

        excludeBtn.addActionListener(e -> showExcludeDialog());

        sendBtn.addActionListener(e -> sendPost());

        JPanel composeActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        composeActions.setOpaque(false);
        composeActions.add(excludeBtn);
        composeActions.add(sendBtn);

        JPanel composePanel = new JPanel(new BorderLayout(10, 0));
        composePanel.setOpaque(false);
        composePanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        composePanel.add(composerScroll, BorderLayout.CENTER);
        composePanel.add(composeActions, BorderLayout.EAST);

        wrap.add(top, BorderLayout.NORTH);
        wrap.add(postsScroll, BorderLayout.CENTER);
        wrap.add(composePanel, BorderLayout.SOUTH);
        return wrap;
    }

    private void openTopic(long topicId, String title) {
        this.currentTopicId = topicId;
        this.currentView = "thread";
        threadTitle.setText(title);
        excludedUserIds.clear();
        inner.show(innerHost, "thread");
        refreshThread();
    }

    private void refreshThread() {
        long topicId = currentTopicId;
        new SwingWorker<JSONArray, Void>() {
            boolean fromCache = false;
            boolean serverError = false; // server reachable but errored, vs. no network at all

            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.topicPosts(topicId);
                    JSONArray data = response.optJSONArray("data", null);
                    JSONArray posts = data != null ? data : response.optJSONArray("posts", new JSONArray());
                    ctx.store.cachePosts(topicId, posts);
                    return posts;
                } catch (ApiOfflineException e) {
                    fromCache = true;
                    JSONArray arr = new JSONArray();
                    ctx.store.cachedPosts(topicId).forEach(arr::put);
                    return arr;
                } catch (ApiException e) {
                    // Server reachable but errored - don't touch the UI with a
                    // partial/empty result, leave what's on screen alone.
                    serverError = true;
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    JSONArray posts = get();
                    if (serverError) {
                        showTransientNotice("Couldn't refresh this thread \u2013 server error. Still showing what was last loaded.");
                        return;
                    }
                    renderThread(posts, fromCache);
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void showTransientNotice(String text) {
        JLabel notice = new JLabel(text);
        notice.setForeground(Theme.WARN);
        notice.setFont(Theme.SMALL_FONT);
        postsBody.add(notice, 0);
        postsBody.revalidate();
        postsBody.repaint();
        javax.swing.Timer t = new javax.swing.Timer(4000, e -> {
            postsBody.remove(notice);
            postsBody.revalidate();
            postsBody.repaint();
        });
        t.setRepeats(false);
        t.start();
    }
     private void renderThread(JSONArray posts, boolean fromCache) {
        postsBody.removeAll();
        if (fromCache) {
            JLabel notice = new JLabel("Offline \u2013 showing this thread's last synced posts. New posts you send now will queue and go out once you're back online.");
            notice.setForeground(Theme.WARN);
            notice.setFont(Theme.SMALL_FONT);
            postsBody.add(notice);
            postsBody.add(Box.createVerticalStrut(8));
        }
        for (int i = posts.length() - 1; i >= 0; i--) {
            JSONObject post = posts.getJSONObject(i);
            postsBody.add(postCard(post));
            postsBody.add(Box.createVerticalStrut(8));

            JSONArray replies = post.optJSONArray("replies");
            if (replies != null) {
                for (int j = 0; j < replies.length(); j++) {
                    postsBody.add(replyCard(replies.getJSONObject(j)));
                    postsBody.add(Box.createVerticalStrut(8));
                }
            }
        }
        postsBody.revalidate();
        postsBody.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vBar = postsScroll.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
        });
    }

    private JComponent postCard(JSONObject post) {
        JSONObject author = post.optJSONObject("author");
        String authorName = author != null ? author.optString("full_name", "Unknown") : "Unknown";
        long authorId = author != null ? author.optLong("user_id", -1) : -1;
        boolean isMine = authorId != -1 && authorId == ctx.session.userId();
        long postId = post.optLong("post_id", -1);
        String syncStatus = post.optString("sync_status", "");
        boolean localOnly = postId < 0 || !syncStatus.isBlank();

        CardPanel bubble = new CardPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground("failed".equals(syncStatus) ? new Color(0xFDECEA) : isMine ? Theme.BUBBLE_MINE : Theme.WHITE);
        bubble.setMaximumSize(new Dimension(440, Integer.MAX_VALUE));

        if (!isMine) {
            JLabel nameLabel = new JLabel(authorName);
            nameLabel.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
            nameLabel.setForeground(Theme.ACCENT_DARK);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(nameLabel);
            bubble.add(Box.createVerticalStrut(3));
        }

        JTextArea content = new JTextArea(post.optString("content", ""));
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setOpaque(false);
        content.setFont(Theme.BODY_FONT);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setBorder(null);
        content.setMargin(new Insets(0, 0, 0, 0));
        bubble.add(content);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton replyBtn = Buttons.link("Reply", Theme.ACCENT_DARK);
        replyBtn.addActionListener(e -> quickReply(postId));
        JButton shareBtn = Buttons.link("Forward", Theme.ACCENT_DARK);
        shareBtn.addActionListener(e -> shareMenu(postId));
        JButton flagBtn = Buttons.link("Flag", Theme.WARN);
        flagBtn.addActionListener(e -> flagPost(postId));

        JLabel time = new JLabel(post.optString("posted_at", ""));
        time.setFont(Theme.SMALL_FONT);
        time.setForeground(Theme.MUTED);

        if (!localOnly) {
            meta.add(replyBtn);
            meta.add(shareBtn);
            meta.add(flagBtn);
            meta.add(time);
        }
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(meta);
        addSyncStatus(bubble, syncStatus, post.optString("sync_error", ""));

        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(bubble);
        return row;
    }

    // Replies use their own id space (reply_id, not post_id) and their own
    // timestamp field (replied_at), and flag through /replies/{id}/flag —
    // not /posts/{id}/flag — so this is a small variant of postCard()
    // rather than a straight reuse.
    private JComponent replyCard(JSONObject reply) {
        JSONObject author = reply.optJSONObject("author");
        String authorName = author != null ? author.optString("full_name", "Unknown") : "Unknown";
        long authorId = author != null ? author.optLong("user_id", -1) : -1;
        boolean isMine = authorId != -1 && authorId == ctx.session.userId();
        long replyId = reply.optLong("reply_id", -1);
        boolean flagged = reply.optBoolean("is_flagged", false);
        String syncStatus = reply.optString("sync_status", "");
        boolean localOnly = replyId < 0 || !syncStatus.isBlank();

        CardPanel bubble = new CardPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground("failed".equals(syncStatus) ? new Color(0xFDECEA) : isMine ? Theme.BUBBLE_MINE : Theme.WHITE);
        bubble.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        if (!isMine) {
            JLabel nameLabel = new JLabel(authorName);
            nameLabel.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
            nameLabel.setForeground(Theme.ACCENT_DARK);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(nameLabel);
            bubble.add(Box.createVerticalStrut(3));
        }

        JTextArea content = new JTextArea(reply.optString("content", ""));
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setOpaque(false);
        content.setFont(Theme.BODY_FONT);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setBorder(null);
        content.setMargin(new Insets(0, 0, 0, 0));
        bubble.add(content);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton shareBtn = Buttons.link("Forward", Theme.ACCENT_DARK);
        shareBtn.addActionListener(e -> shareReplyMenu(replyId));
        JButton flagBtn = Buttons.link(flagged ? "Flagged" : "Flag", Theme.WARN);
        flagBtn.addActionListener(e -> flagReply(replyId));

        JLabel time = new JLabel(reply.optString("replied_at", ""));
        time.setFont(Theme.SMALL_FONT);
        time.setForeground(Theme.MUTED);

        if (!localOnly) {
            meta.add(shareBtn);
            meta.add(flagBtn);
            meta.add(time);
        }
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(meta);
        addSyncStatus(bubble, syncStatus, reply.optString("sync_error", ""));

        // Indented under its parent post, mirroring the web client's
        // .is-reply connecting-line treatment.
        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, isMine ? 0 : 26, 0, isMine ? 26 : 0));
        row.add(bubble);
        return row;
    }

    private void quickReply(long postId) {
        if (postId < 0) return;
        String text = JOptionPane.showInputDialog(this, "Reply:", "Reply", JOptionPane.PLAIN_MESSAGE);
        if (text == null || text.isBlank()) return;
        String clientRef = java.util.UUID.randomUUID().toString();
        new SwingWorker<Void, Void>() {
            String errorMessage = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.createReply(postId, text.trim(), clientRef);
                } catch (ApiOfflineException e) {
                    JSONObject payload = new JSONObject().put("post_id", postId).put("content", text.trim()).put("client_ref", clientRef);
                    long outboxId = ctx.store.queueOutboxAction("create_reply", payload);
                    ctx.store.cachePendingReply(postId, outboxId, payload, ctx.session.user());
                } catch (ApiException e) {
                    errorMessage = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (errorMessage != null) {
                    JOptionPane.showMessageDialog(TopicWorkspacePanel.this,
                            "Reply could not be sent: " + errorMessage,
                            "Send failed", JOptionPane.ERROR_MESSAGE);
                }
                refreshThread();
            }
        }.execute();
    }

    // Values MUST exactly match the server's validation list —
    // platform => required|in:WhatsApp,Twitter,Facebook,LinkedIn,Clipboard,Other
    // (SocialShareController::store/storeReply) — anything else is
    // rejected with a 422 before it ever reaches the database.
    private void addSyncStatus(JPanel bubble, String syncStatus, String syncError) {
        if (syncStatus == null || syncStatus.isBlank()) return;
        String text = "failed".equals(syncStatus)
                ? "Failed to sync" + (syncError == null || syncError.isBlank() ? "" : ": " + syncError)
                : "Pending";
        JLabel status = new JLabel(text);
        status.setFont(Theme.SMALL_FONT);
        status.setForeground(Theme.WARN); // red for both pending and failed
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(status);
    }

    private static final String[] SHARE_PLATFORMS = {"WhatsApp", "Twitter", "Facebook", "LinkedIn", "Clipboard"};

    private void shareMenu(long postId) {
        if (postId < 0) return;
        String choice = (String) JOptionPane.showInputDialog(this, "Share to:", "Forward Post",
                JOptionPane.PLAIN_MESSAGE, null, SHARE_PLATFORMS, SHARE_PLATFORMS[0]);
        if (choice == null) return;
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.sharePost(postId, choice);
                } catch (ApiException e) {
                    publish(e.getMessage());
                } catch (ApiOfflineException e) {
                    publish("You're offline — sharing needs a live connection.");
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    JOptionPane.showMessageDialog(TopicWorkspacePanel.this, chunks.get(chunks.size() - 1),
                            "Couldn't share", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void shareReplyMenu(long replyId) {
        if (replyId < 0) return;
        String choice = (String) JOptionPane.showInputDialog(this, "Share to:", "Forward Reply",
                JOptionPane.PLAIN_MESSAGE, null, SHARE_PLATFORMS, SHARE_PLATFORMS[0]);
        if (choice == null) return;
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.shareReply(replyId, choice);
                } catch (ApiException e) {
                    publish(e.getMessage());
                } catch (ApiOfflineException e) {
                    publish("You're offline — sharing needs a live connection.");
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    JOptionPane.showMessageDialog(TopicWorkspacePanel.this, chunks.get(chunks.size() - 1),
                            "Couldn't share", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void flagPost(long postId) {
        if (postId < 0) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.flagPost(postId);
                } catch (ApiException | ApiOfflineException ignored) {
                }
                return null;
            }

            @Override
            protected void done() {
                refreshThread();
            }
        }.execute();
    }

    private void flagReply(long replyId) {
        if (replyId < 0) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.flagReply(replyId);
                } catch (ApiException | ApiOfflineException ignored) {
                }
                return null;
            }

            @Override
            protected void done() {
                refreshThread();
            }
        }.execute();
    }

    private void showExcludeDialog() {
        long groupId = currentGroupId;
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.groupMembers(groupId);
                    JSONArray data = response.optJSONArray("data", null);
                    return data != null ? data : new JSONArray();
                } catch (ApiException | ApiOfflineException e) {
                    return new JSONArray();
                }
            }

            @Override
            protected void done() {
                JSONArray members;
                try {
                    members = get();
                } catch (Exception e) {
                    members = new JSONArray();
                }
                openExcludeDialog(members);
            }
        }.execute();
    }

    private void openExcludeDialog(JSONArray members) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Exclude members from your next post", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.getContentPane().setBackground(Theme.WHITE);
        dialog.setLayout(new BorderLayout());

        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(Theme.WHITE);
        card.setBorder(new EmptyBorder(24, 26, 20, 26));

        JLabel title = new JLabel("Exclude members from your next post");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);
        card.add(title, BorderLayout.NORTH);

        JPanel listBody = new JPanel();
        listBody.setLayout(new BoxLayout(listBody, BoxLayout.Y_AXIS));
        listBody.setBackground(Theme.WHITE);

        java.util.List<JCheckBox> boxes = new java.util.ArrayList<>();
        if (members.isEmpty()) {
            JLabel empty = new JLabel("No other members in this group.");
            empty.setFont(Theme.BODY_FONT);
            empty.setForeground(Theme.MUTED);
            listBody.add(empty);
        } else {
            for (int i = 0; i < members.length(); i++) {
                JSONObject m = members.getJSONObject(i);
                long uid = m.optLong("user_id", -1);
                JCheckBox cb = new JCheckBox(m.optString("full_name", "Member"));
                cb.setFont(Theme.BODY_FONT);
                cb.setForeground(Theme.INK);
                cb.setOpaque(false);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                cb.setSelected(excludedUserIds.contains(uid));
                cb.putClientProperty("userId", uid);
                cb.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                        new EmptyBorder(10, 4, 10, 4)));
                cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                boxes.add(cb);
                listBody.add(cb);
            }
        }

        JScrollPane scroll = new JScrollPane(listBody);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(380, 260));
        card.add(scroll, BorderLayout.CENTER);

        JButton cancel = Buttons.secondary("Cancel");
        cancel.addActionListener(e -> dialog.dispose());

        JButton ok = Buttons.primary("OK");
        ok.addActionListener(e -> {
            excludedUserIds.clear();
            for (JCheckBox cb : boxes) {
                if (cb.isSelected()) excludedUserIds.add((Long) cb.getClientProperty("userId"));
            }
            excludeBtn.setText(excludedUserIds.isEmpty() ? "Exclude members" : "Excluding " + excludedUserIds.size());
            dialog.dispose();
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(cancel);
        footer.add(ok);
        card.add(footer, BorderLayout.SOUTH);

        dialog.add(card, BorderLayout.CENTER);
        dialog.setSize(440, 420);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    private JComponent buildSearchBar() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 0));
        wrapper.setBackground(Theme.WHITE);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField.setBorder(null);
        searchField.setOpaque(false);
        searchField.setFont(Theme.BODY_FONT);
        searchField.setForeground(Theme.INK);

        JLabel icon = new JLabel("\uD83D\uDD0D"); // 🔍
        icon.setFont(Theme.BODY_FONT);
        icon.setForeground(Theme.MUTED);

        wrapper.add(searchField, BorderLayout.CENTER);
        wrapper.add(icon, BorderLayout.EAST);

        // Placeholder text (Swing has no native placeholder attribute)
        searchField.setText("Search topics...");
        searchField.setForeground(Theme.MUTED);
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) {
                if (searchField.getText().equals("Search topics...")) {
                    searchField.setText("");
                    searchField.setForeground(Theme.INK);
                }
            }
            public void focusLost(java.awt.event.FocusEvent e) {
                if (searchField.getText().isBlank()) {
                    searchField.setText("Search topics...");
                    searchField.setForeground(Theme.MUTED);
                }
            }
        });

        // Debounce: wait 350ms after the last keystroke before hitting the API
        searchDebounce.setRepeats(false);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            private void trigger() {
                searchDebounce.stop();
                for (var l : searchDebounce.getActionListeners()) searchDebounce.removeActionListener(l);
                searchDebounce.addActionListener(ev -> refreshTopicList());
                searchDebounce.start();
            }
        });

        return wrapper;
    }

    private void sendPost() {
        if (!sendBtn.isEnabled()) return; // already sending this post - ignore a repeat click/keypress
        String text = composer.getText().trim();
        if (text.isBlank()) return;
        long topicId = currentTopicId;
        long[] excludeIds = excludedUserIds.stream().mapToLong(Long::longValue).toArray();
        // Stamped once, before any network attempt, so the immediate call AND
        // a later outbox replay of the same compose action always carry the
        // same ref. Previously a timeout on the first attempt (which does NOT
        // guarantee the server never received it) would fall back to the
        // outbox, and replaying that outbox entry could create a second,
        // separate post server-side - that's what caused messages sent while
        // offline/flaky to show up twice after syncing.
        String clientRef = java.util.UUID.randomUUID().toString();

        sendBtn.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            String errorMessage = null;

            @Override
            protected Boolean doInBackground() {
                try {
                    ctx.api.createPost(topicId, text, null, excludeIds, clientRef);
                    return true;
                } catch (ApiOfflineException e) {
                    JSONObject payload = new JSONObject().put("topic_id", topicId).put("content", text).put("client_ref", clientRef);
                    if (excludeIds.length > 0) payload.put("exclude_user_ids", excludeIds);
                    long outboxId = ctx.store.queueOutboxAction("create_post", payload);
                    ctx.store.cachePendingPost(topicId, outboxId, payload, ctx.session.user());
                    return false;
                } catch (ApiException e) {
                    errorMessage = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                sendBtn.setEnabled(true);
                if (errorMessage != null) {
                    JOptionPane.showMessageDialog(TopicWorkspacePanel.this,
                            "Post could not be sent: " + errorMessage,
                            "Send failed", JOptionPane.ERROR_MESSAGE);
                    return; // keep composer text so the user can retry/edit instead of losing it
                }
                composer.setText("");
                excludedUserIds.clear();
                excludeBtn.setText("Exclude members");
                refreshThread();
            }
        }.execute();
    }

    private void exportPdf() {
        long topicId = currentTopicId;
        String topicTitle = threadTitle.getText();

        exportBtnSetEnabled(false);
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                byte[] pdfBytes = ctx.api.downloadTopicPdf(topicId);

                File dir = com.smartforum.desktop.util.AppConfig.exportsDir().toFile();
                if (!dir.exists()) dir.mkdirs();

                String safeTitle = topicTitle == null ? "topic" : topicTitle.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safeTitle.isEmpty()) safeTitle = "topic";
                File file = new File(dir, safeTitle + "-" + topicId + ".pdf");
                java.nio.file.Files.write(file.toPath(), pdfBytes);

                ctx.store.recordCachedFile("topic_pdf", topicId, file.getName(), file.getAbsolutePath());
                return file;
            }

            @Override
            protected void done() {
                exportBtnSetEnabled(true);
                try {
                    File file = get();
                    boolean opened = false;
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        try {
                            Desktop.getDesktop().open(file);
                            opened = true;
                        } catch (Exception ignored) {
                            // Fall through to the "here's the path" dialog below.
                        }
                    }
                    if (!opened) {
                        JOptionPane.showMessageDialog(TopicWorkspacePanel.this,
                                "PDF exported. No default PDF viewer is available to open it automatically, " +
                                        "but you can find it at:\n" + file.getAbsolutePath(),
                                "Export PDF", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause instanceof ApiOfflineException
                            ? "Couldn't reach the server to export this topic. Check your connection and try again."
                            : cause instanceof ApiException
                            ? "Export failed: " + cause.getMessage()
                            : "Couldn't export the PDF: " + cause.getMessage();
                    JOptionPane.showMessageDialog(TopicWorkspacePanel.this, message,
                            "Export PDF", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void exportBtnSetEnabled(boolean enabled) {
        if (exportBtn != null) exportBtn.setEnabled(enabled);
    }
}