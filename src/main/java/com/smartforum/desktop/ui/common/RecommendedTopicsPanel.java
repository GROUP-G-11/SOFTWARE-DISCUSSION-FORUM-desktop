package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public class RecommendedTopicsPanel extends JPanel {

    private final AppContext ctx;
    private final Consumer<Long> onOpenTopic;
    private final JPanel body = new JPanel();

    public RecommendedTopicsPanel(AppContext ctx, Consumer<Long> onOpenTopic) {
        this.ctx = ctx;
        this.onOpenTopic = onOpenTopic;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("Recommended for you");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);
        JLabel sub = new JLabel("Based on your recent activity across your groups.");
        sub.setFont(Theme.SMALL_FONT);
        sub.setForeground(Color.GRAY);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);
        top.add(sub);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(body), BorderLayout.CENTER);
    }

    public void refresh() {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    return ctx.api.recommendations();
                } catch (ApiException | ApiOfflineException e) {
                    return new JSONArray();
                }
            }

            @Override
            protected void done() {
                try {
                    render(get());
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void render(JSONArray recs) {
        body.removeAll();
        if (recs.isEmpty()) {
            JLabel empty = new JLabel("Nothing to recommend yet \u2014 join a few discussions first.");
            empty.setForeground(Color.GRAY);
            body.add(empty);
        }
        for (int i = 0; i < recs.length(); i++) {
            JSONObject r = recs.getJSONObject(i);
            JSONObject topic = r.optJSONObject("topic");
            String title = topic != null ? topic.optString("title", "Untitled topic") : r.optString("title", "Untitled topic");
            long topicId = topic != null ? topic.optLong("topic_id", -1) : r.optLong("topic_id", -1);
            double relevance = r.optDouble("relevance_score", 0);

            JPanel row = new JPanel(new BorderLayout());
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 4, 10, 4)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            JLabel label = new JLabel(title);
            label.setFont(Theme.BODY_FONT_BOLD);
            JLabel score = new JLabel(Math.round(relevance * 100) + "% match");
            score.setFont(Theme.SMALL_FONT);
            score.setForeground(Theme.ACCENT);
            row.add(label, BorderLayout.WEST);
            row.add(score, BorderLayout.EAST);
            if (topicId >= 0) {
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                row.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        onOpenTopic.accept(topicId);
                    }
                });
            }
            body.add(row);
        }
        body.revalidate();
        body.repaint();
    }
}
