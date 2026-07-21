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

public class GroupStatisticsDialog extends JDialog {

    public GroupStatisticsDialog(Window owner, AppContext ctx, long groupId) {
        super(owner, "Group Statistics", ModalityType.APPLICATION_MODAL);
        setSize(560, 520);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.WHITE);

        JLabel title = new JLabel("Loading\u2026");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);

        JPanel metricsPanel = new JPanel(new GridLayout(2, 2, 12, 12));
        metricsPanel.setOpaque(false);
        metricsPanel.setBorder(new EmptyBorder(14, 0, 14, 0));

        JLabel strugglingTitle = new JLabel("Struggling students (idle 7+ days)");
        strugglingTitle.setFont(Theme.BODY_FONT_BOLD);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Name", "Last active"}, 0);
        JTable table = new JTable(model);
        table.setRowHeight(24);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(title, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(metricsPanel, BorderLayout.NORTH);
        JPanel strugglingWrap = new JPanel(new BorderLayout(0, 6));
        strugglingWrap.setOpaque(false);
        strugglingWrap.add(strugglingTitle, BorderLayout.NORTH);
        strugglingWrap.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(strugglingWrap, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(20, 24, 20, 24));
        add(content, BorderLayout.CENTER);

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
                    metricsPanel.add(new JLabel(stats != null ? stats.optString("message") : "You do not have access to this group's statistics."));
                    return;
                }
                title.setText(stats.optString("group", "Group") + " \u2014 analytics");
                metricsPanel.add(metric("Total posts", stats.opt("total_posts")));
                metricsPanel.add(metric("Active contributors (7 days)", stats.opt("active_contributors")));
                metricsPanel.add(metric("Currently banned", stats.opt("banned_individuals")));
                metricsPanel.add(metric("Unanswered topics", stats.opt("unanswered_topics")));

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
