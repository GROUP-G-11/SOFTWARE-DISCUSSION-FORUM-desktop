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

    // Mirrors dashboard/admin.blade.php's inline "Visual Charts" feature
    // (#groupStatsVisualization / viewInlineGroupStats()): a chart card that
    // toggles in place of the groups list, rather than a separate page, so
    // clicking "Visual Charts" on a row shows a bar chart for that group
    // right here with a "Hide Charts" button to come back.
    private final JScrollPane listScrollPane;
    private final BarChartPanel chart = new BarChartPanel();
    private final JLabel chartCardTitle = new JLabel("Group Statistics Visualization");
    private final JPanel chartCard;
    private static final Color[] CHART_COLORS = {
            new Color(0x367EEB),
            new Color(0x4BC0C0),
            new Color(0xFFCE56),
            new Color(0xFF40DF),
            new Color(0x9966FF)
    };

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
        listScrollPane = new JScrollPane(listContainer);

        // Chart card: header (title + Hide Charts) + the bar chart itself.
        // Hidden until "Visual Charts" is clicked on a row, matching the
        // Laravel version's display:none default.
        JButton hideCharts = Buttons.secondary("Hide Charts");
        hideCharts.addActionListener(e -> closeGroupStatsView());
        chartCardTitle.setFont(Theme.BODY_FONT_BOLD);
        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.setOpaque(false);
        chartHeader.add(chartCardTitle, BorderLayout.WEST);
        JPanel hideRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        hideRow.setOpaque(false);
        hideRow.add(hideCharts);
        chartHeader.add(hideRow, BorderLayout.EAST);

        chart.setPreferredSize(new Dimension(100, 260));
        chart.setBorder(new EmptyBorder(10, 4, 4, 4));

        chartCard = new JPanel(new BorderLayout(0, 10));
        chartCard.setBackground(Theme.PAPER_DIM);
        chartCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        chartCard.add(chartHeader, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);
        chartCard.setVisible(false);

        add(top, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(chartCard, BorderLayout.NORTH);
        center.add(listScrollPane, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    /** Fetches live metrics for a group and shows the inline chart card, hiding the groups list underneath. */
    private void viewInlineGroupStats(long groupId, String groupName) {
        chartCardTitle.setText(groupName + " \u2014 Metric Analysis Charts");
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.groupStatistics(groupId);
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONObject stats;
                try {
                    stats = get();
                } catch (Exception e) {
                    stats = null;
                }
                if (stats == null || stats.has("message")) {
                    JOptionPane.showMessageDialog(GroupsPanel.this,
                            stats != null ? stats.optString("message") : "Couldn't load statistics for this group.",
                            "Visual Charts", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                chart.setData(
                        new String[]{"Members Count", "Topics", "Published Posts", "Internal Replies", "Active Users"},
                        new double[]{
                                stats.optDouble("members_count", 0),
                                stats.optDouble("topics_count", 0),
                                stats.optDouble("total_posts", 0),
                                stats.optDouble("replies_count", 0),
                                stats.optDouble("active_contributors", 0)
                        },
                        CHART_COLORS);
                chartCard.setVisible(true);
                listScrollPane.setVisible(false);
                revalidate();
                repaint();
            }
        }.execute();
    }

    private void closeGroupStatsView() {
        chartCard.setVisible(false);
        listScrollPane.setVisible(true);
        revalidate();
        repaint();
    }

    public void refresh() {
        closeGroupStatsView();
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
            JButton visualCharts = Buttons.primary("Visual Charts");
            visualCharts.addActionListener(e -> viewInlineGroupStats(groupId, groupName));
            right.add(stats);
            right.add(gradebook);
            right.add(visualCharts);
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
