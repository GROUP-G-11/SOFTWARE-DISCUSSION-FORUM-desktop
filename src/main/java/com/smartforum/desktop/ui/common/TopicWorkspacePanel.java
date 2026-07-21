package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Desktop;
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

    private long currentGroupId = -1;
    private long currentTopicId = -1;
    private String currentGroupName = "Group";

    public TopicWorkspacePanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout());
        setBackground(Theme.WHITE);
        add(innerHost, BorderLayout.CENTER);

        innerHost.add(buildTopicListView(), "list");
        innerHost.add(buildThreadView(), "thread");
    }

    public void openGroup(long groupId, String groupName) {
        this.currentGroupId = groupId;
        this.currentGroupName = groupName == null ? "Group" : groupName;
        groupTitle.setText(groupName == null ? "Topics" : groupName + " \u2014 Topics");
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

        wrap.add(top, BorderLayout.NORTH);
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
                    JSONObject response = ctx.api.listTopics(groupId, null, null);
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
        JLabel title = new JLabel(topic.optString("title", "Untitled topic"));
        title.setFont(Theme.BODY_FONT_BOLD.deriveFont(14f));
        JLabel meta = new JLabel(topic.optInt("posts_count", 0) + " replies" + (topic.has("category") && !topic.isNull("category") ? "  \u00b7  " + topic.optString("category") : ""));
        meta.setFont(Theme.SMALL_FONT);
        meta.setForeground(Color.GRAY);
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
    private final JTextArea composer = new JTextArea(1, 30);
    private final JButton excludeBtn = Buttons.secondary("Exclude members");
    private final java.util.Set<Long> excludedUserIds = new java.util.HashSet<>();

    private JComponent buildThreadView() {
        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setBorder(new EmptyBorder(24, 28, 24, 28));
        wrap.setBackground(Theme.WHITE);

        JButton back = Buttons.secondary("\u2190 Back to topics");
        back.addActionListener(e -> inner.show(innerHost, "list"));

        threadTitle.setFont(Theme.HEADING_FONT);
        threadTitle.setForeground(Theme.INK);

        JButton exportBtn = Buttons.secondary("Export PDF");
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
        JScrollPane postsScroll = new JScrollPane(postsBody);
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

        JButton sendBtn = Buttons.primary("Send");
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
        threadTitle.setText(title);
        excludedUserIds.clear();
        inner.show(innerHost, "thread");
        refreshThread();
    }

    private void refreshThread() {
        long topicId = currentTopicId;
        new SwingWorker<JSONArray, Void>() {
            boolean fromCache = false;

            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.topicPosts(topicId);
                    JSONArray data = response.optJSONArray("data", null);
                    JSONArray posts = data != null ? data : response.optJSONArray("posts", new JSONArray());
                    ctx.store.cachePosts(topicId, posts);
                    return posts;
                } catch (ApiOfflineException | ApiException e) {
                    fromCache = true;
                    JSONArray arr = new JSONArray();
                    ctx.store.cachedPosts(topicId).forEach(arr::put);
                    return arr;
                }
            }

            @Override
            protected void done() {
                try {
                    renderThread(get(), fromCache);
                } catch (Exception ignored) {
                }
            }
        }.execute();
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
        for (int i = 0; i < posts.length(); i++) {
            postsBody.add(postCard(posts.getJSONObject(i)));
            postsBody.add(Box.createVerticalStrut(8));
        }
        postsBody.revalidate();
        postsBody.repaint();
    }

    private JComponent postCard(JSONObject post) {
        JSONObject author = post.optJSONObject("author");
        String authorName = author != null ? author.optString("full_name", "Unknown") : "Unknown";
        long authorId = author != null ? author.optLong("user_id", -1) : -1;
        boolean isMine = authorId != -1 && authorId == ctx.session.userId();
        long postId = post.optLong("post_id", -1);

        CardPanel bubble = new CardPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(isMine ? Theme.BUBBLE_MINE : Theme.WHITE);
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

        meta.add(replyBtn);
        meta.add(shareBtn);
        meta.add(flagBtn);
        meta.add(time);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(meta);

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

    private void quickReply(long postId) {
        if (postId < 0) return;
        String text = JOptionPane.showInputDialog(this, "Reply:", "Reply", JOptionPane.PLAIN_MESSAGE);
        if (text == null || text.isBlank()) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.createReply(postId, text.trim());
                } catch (ApiOfflineException e) {
                    JSONObject payload = new JSONObject().put("post_id", postId).put("content", text.trim());
                    ctx.store.queueOutboxAction("create_reply", payload);
                } catch (ApiException ignored) {
                }
                return null;
            }

            @Override
            protected void done() {
                refreshThread();
            }
        }.execute();
    }

    private void shareMenu(long postId) {
        if (postId < 0) return;
        String[] options = {"WhatsApp", "X", "LinkedIn", "Copy link"};
        String choice = (String) JOptionPane.showInputDialog(this, "Share to:", "Forward Post",
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == null) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.sharePost(postId, choice);
                } catch (ApiException | ApiOfflineException ignored) {
                }
                return null;
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

    private void sendPost() {
        String text = composer.getText().trim();
        if (text.isBlank()) return;
        long topicId = currentTopicId;
        long[] excludeIds = excludedUserIds.stream().mapToLong(Long::longValue).toArray();

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    ctx.api.createPost(topicId, text, null, excludeIds);
                    return true;
                } catch (ApiOfflineException e) {
                    JSONObject payload = new JSONObject().put("topic_id", topicId).put("content", text);
                    if (excludeIds.length > 0) payload.put("exclude_user_ids", excludeIds);
                    ctx.store.queueOutboxAction("create_post", payload);
                    return false;
                } catch (ApiException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                composer.setText("");
                excludedUserIds.clear();
                excludeBtn.setText("Exclude members");
                refreshThread();
            }
        }.execute();
    }

    private void exportPdf() {
        long topicId = currentTopicId;
        String url = ctx.api.downloadTopicPdfUrl(topicId);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url + "?token=" + ctx.api.getBearerToken()));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't open the export link. You can also download it directly from:\n" + url,
                    "Export PDF", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}