package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Full-page version of the old GroupStatisticsDialog. Registered as a
 * CardLayout page in DashboardChrome instead of opened as a JDialog popup,
 * matching how Laravel gives statistics its own route/page rather than a
 * modal. Call open(groupId) right after chrome.showPanel("group-statistics").
 */
public class GroupStatisticsPanel extends JPanel {

    private final AppContext ctx;
    private final JLabel title = new JLabel("Loading\u2026");
    private final JPanel metricsPanel = new JPanel(new GridLayout(2, 2, 12, 12));
    private final DefaultTableModel model =
            new DefaultTableModel(new Object[]{"Name", "Last active"}, 0);

    public GroupStatisticsPanel(AppContext ctx, Runnable onBack) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JButton back = Buttons.link("\u2190 Back to Groups", Theme.SKY);
        back.addActionListener(e -> onBack.run());

        title.setFont(Theme.HEADING_FONT.deriveFont(22f));
        title.setForeground(Theme.INK);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backRow.setOpaque(false);
        backRow.add(back);
        header.add(backRow);
        header.add(Box.createVerticalStrut(6));
        header.add(title);

        metricsPanel.setOpaque(false);
        metricsPanel.setBorder(new EmptyBorder(14, 0, 14, 0));

        JLabel strugglingTitle = new JLabel("Struggling students (idle 7+ days)");
        strugglingTitle.setFont(Theme.BODY_FONT_BOLD);
        JTable table = new JTable(model);
        table.setRowHeight(26);

        JPanel strugglingWrap = new JPanel(new BorderLayout(0, 6));
        strugglingWrap.setOpaque(false);
        strugglingWrap.add(strugglingTitle, BorderLayout.NORTH);
        strugglingWrap.add(new JScrollPane(table), BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(metricsPanel, BorderLayout.NORTH);
        center.add(strugglingWrap, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    /** Call this right after navigating to this page for a given group. */
    public void open(long groupId) {
        title.setText("Loading\u2026");
        metricsPanel.removeAll();
        model.setRowCount(0);
        metricsPanel.revalidate();
        metricsPanel.repaint();

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
                    title.setText("Access denied");
                    metricsPanel.add(new JLabel(stats != null
                            ? stats.optString("message")
                            : "You do not have access to this group's statistics."));
                    metricsPanel.revalidate();
                    metricsPanel.repaint();
                    return;
                }
                title.setText(stats.optString("group", "Group") + " \u2014 analytics");
                metricsPanel.add(metric("Total posts", stats.opt("total_posts")));
                metricsPanel.add(metric("Active contributors (7 days)", stats.opt("active_contributors")));
                metricsPanel.add(metric("Currently banned", stats.opt("banned_individuals")));
                metricsPanel.add(metric("Unanswered topics", stats.opt("unanswered_topics")));
                metricsPanel.revalidate();
                metricsPanel.repaint();

                JSONArray struggling = stats.optJSONArray("struggling_students", new JSONArray());
                for (int i = 0; i < struggling.length(); i++) {
                    JSONObject s = struggling.getJSONObject(i);
                    String lastActive = s.isNull("last_active_at") || !s.has("last_active_at")
                            ? "Never active" : s.optString("last_active_at");
                    model.addRow(new Object[]{s.optString("full_name", "Student"), lastActive});
                }
            }
        }.execute();
    }

    private JComponent metric(String label, Object value) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.PAPER_DIM);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel v = new JLabel(String.valueOf(value));
        v.setFont(Theme.HEADING_FONT_SM);
        v.setForeground(Theme.INK);
        JLabel l = new JLabel(label);
        l.setFont(Theme.SMALL_FONT);
        l.setForeground(Theme.MUTED);
        card.add(v);
        card.add(l);
        return card;
    }
}
