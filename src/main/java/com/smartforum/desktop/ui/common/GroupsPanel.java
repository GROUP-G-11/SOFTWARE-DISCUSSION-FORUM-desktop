package com.smartforum.desktop.ui.common;
 
import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;
 
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
 
/**
 * Groups Workspace, with real per-role behavior taken directly from the
 * three dashboard blade files rather than one generic view:
 *   - STUDENT: join / joined+open into topics (dashboard/student.blade.php)
 *   - LECTURER: join / open into topics, plus Statistics+Gradebook links
 *     when they own or administer the group (dashboard/lecturer.blade.php
 *     groupsViewHtml())
 *   - ADMIN: no click-through to messages at all - "Group name is plain
 *     text (no link into the general group/posts page, which is where
 *     messaging lives) — Statistics and Gradebook stay as admin-facing
 *     actions." (dashboard/admin.blade.php loadGroups(), verbatim comment)
 *
 * Joining a group now requires admin approval (GroupJoinRequest) rather
 * than being instant - see join()/PENDING pill below, and
 * JoinRequestsDialog for the group-admin side of approving/declining.
 */
public class GroupsPanel extends JPanel {
 
    public enum Mode { STUDENT, LECTURER, ADMIN }
 
    private final AppContext ctx;
    private final Mode mode;
    private final Consumer<Long> onOpenGroup; // ignored in ADMIN mode
    private final Consumer<Long> onViewStatistics;
    private final Consumer<Long> onViewGradebook;
    private final JPanel listContainer = new JPanel();
    private final JLabel offlineNotice = new JLabel();
 
    public GroupsPanel(AppContext ctx, Mode mode, Consumer<Long> onOpenGroup,
                        Consumer<Long> onViewStatistics, Consumer<Long> onViewGradebook) {
        this.ctx = ctx;
        this.mode = mode;
        this.onOpenGroup = onOpenGroup;
        this.onViewStatistics = onViewStatistics;
        this.onViewGradebook = onViewGradebook;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);
 
        JLabel title = new JLabel("Groups");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);
 
        JLabel sub = new JLabel(mode == Mode.ADMIN
                ? "As an administrator you can view statistics and the gradebook for every group on the platform."
                : mode == Mode.LECTURER
                    ? "Groups you own or administer. Statistics and the gradebook are only available for groups where you're the lecturer or an active group admin."
                    : " ");
        sub.setFont(Theme.SMALL_FONT);
        sub.setForeground(Color.GRAY);
 
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.add(title, BorderLayout.WEST);
        if (mode != Mode.ADMIN) {
            JButton createBtn = Buttons.primary("+ Create Group");
            createBtn.addActionListener(e -> showCreateDialog());
            JPanel createRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            createRow.setOpaque(false);
            createRow.add(createBtn);
            headerRow.add(createRow, BorderLayout.EAST);
        }
        top.add(headerRow);
        top.add(sub);
 
        offlineNotice.setForeground(Theme.WARN);
        offlineNotice.setFont(Theme.SMALL_FONT);
        offlineNotice.setVisible(false);
        top.add(offlineNotice);
 
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);
 
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(listContainer), BorderLayout.CENTER);
    }
 
    public void refresh() {
        new SwingWorker<JSONArray, Void>() {
            boolean fromCache = false;
 
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listGroups();
                    JSONArray data = response.optJSONArray("data", null);
                    JSONArray groups = data != null ? data : new JSONArray();
                    ctx.store.cacheGroups(groups);
                    return groups;
                } catch (ApiOfflineException | ApiException e) {
                    fromCache = true;
                    List<JSONObject> cached = ctx.store.cachedGroups();
                    JSONArray arr = new JSONArray();
                    cached.forEach(arr::put);
                    return arr;
                }
            }
 
            @Override
            protected void done() {
                try {
                    render(get(), fromCache);
                } catch (Exception e) {
                    render(new JSONArray(), true);
                }
            }
        }.execute();
    }
 
    private void render(JSONArray groups, boolean fromCache) {
        offlineNotice.setVisible(fromCache);
        offlineNotice.setText(fromCache ? "You're offline \u2013 showing the last groups list saved to this device." : "");
 
        listContainer.removeAll();
        for (int i = 0; i < groups.length(); i++) {
            listContainer.add(groupRow(groups.getJSONObject(i)));
            listContainer.add(Box.createVerticalStrut(1));
        }
        listContainer.revalidate();
        listContainer.repaint();
    }
 
    private JComponent groupRow(JSONObject group) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(12, 4, 12, 4)));
        row.setBackground(Theme.WHITE);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
 
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
 
        JLabel name = new JLabel(group.optString("name", "Untitled group"));
        name.setFont(Theme.BODY_FONT_BOLD.deriveFont(15f));
 
        JLabel meta = new JLabel(group.optInt("members_count", 0) + " members \u00b7 " + group.optInt("topics_count", 0) + " topics");
        meta.setFont(Theme.SMALL_FONT);
        meta.setForeground(Color.GRAY);
 
        left.add(name);
        left.add(meta);
 
        boolean isBanned = group.optBoolean("is_banned", false);
        boolean isMember = group.optBoolean("is_member", false);
        boolean hasPendingRequest = group.optBoolean("has_pending_request", false);
        boolean canViewStats = group.optBoolean("is_owner", false) || group.optBoolean("is_group_admin", false);
        long groupId = group.getLong("group_id");
        String groupName = group.optString("name", "Group");
 
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
 
        if (mode == Mode.ADMIN) {
            // Administrators always have statistics/gradebook access
            // (StatisticsController::authorizeGroupAccess() allows
            // Administrator unconditionally) - no join/open controls at all.
            JButton stats = Buttons.secondary("Statistics");
            stats.addActionListener(e -> onViewStatistics.accept(groupId));
            JButton gradebook = Buttons.secondary("Gradebook");
            gradebook.addActionListener(e -> onViewGradebook.accept(groupId));
            right.add(stats);
            right.add(gradebook);
        } else if (isBanned) {
            right.add(Buttons.pill("BANNED", new Color(0xFDEAEA), Theme.WARN));
        } else if (isMember) {
            right.add(Buttons.pill("JOINED", Theme.SKY_DIM, Theme.SKY));
            if (mode == Mode.LECTURER && canViewStats) {
                JButton stats = Buttons.secondary("Statistics");
                stats.addActionListener(e -> onViewStatistics.accept(groupId));
                JButton gradebook = Buttons.secondary("Gradebook");
                gradebook.addActionListener(e -> onViewGradebook.accept(groupId));
                right.add(stats);
                right.add(gradebook);
            }
            // Any group admin/owner (student or lecturer) can approve or
            // decline pending join requests - mirrors the web client's
            // Group Admin panel section, per authorizeGroupAdmin() server-side.
            if (canViewStats) {
                JButton requests = Buttons.secondary("Join Requests");
                requests.addActionListener(e ->
                        new JoinRequestsDialog(SwingUtilities.getWindowAncestor(this), ctx, groupId, groupName, this::refresh).setVisible(true));
                right.add(requests);
            }
            JButton open = Buttons.secondary("Open");
            open.addActionListener(e -> onOpenGroup.accept(groupId));
            right.add(open);
        } else if (hasPendingRequest) {
            right.add(Buttons.pill("PENDING", new Color(0xFEF3C7), new Color(0xB45309)));
        } else {
            JButton join = Buttons.primary("Join");
            join.addActionListener(e -> join(groupId));
            right.add(join);
        }
 
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }
 
    private void join(long groupId) {
        new SwingWorker<Void, Void>() {
            String error = null;
 
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.joinGroup(groupId);
                } catch (ApiOfflineException e) {
                    error = "Joining a group needs an internet connection. Please try again once you're back online.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }
 
            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(GroupsPanel.this, error, "Couldn't join group", JOptionPane.WARNING_MESSAGE);
                } else {
                    // Success now means "request sent for approval", not
                    // instant membership - refresh() will pick up
                    // has_pending_request from the server and show PENDING.
                    refresh();
                }
            }
        }.execute();
    }
 
    private void showCreateDialog() {
        new CreateGroupDialog(SwingUtilities.getWindowAncestor(this), ctx, this::refresh).setVisible(true);
    }
}
 