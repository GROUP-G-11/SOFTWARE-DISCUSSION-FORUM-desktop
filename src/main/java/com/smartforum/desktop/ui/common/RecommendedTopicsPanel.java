package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class RecommendedTopicsPanel extends JPanel {

    private final AppContext ctx;
    private final Consumer<Long> onOpenTopic;
    private final JPanel body = new JPanel();

    // UI Colors matching Laravel design
    private static final Color PAGE_BG = Color.WHITE;
    private static final Color CARD_BG = Color.WHITE;
    private static final Color CARD_BORDER = new Color(225, 228, 232);
    private static final Color TITLE_COLOR = new Color(20, 35, 80); // Dark purple link
    private static final Color SUB_TEXT_COLOR = new Color(110, 115, 125);
    private static final Color BADGE_BG = new Color(38, 92, 78); // Dark Green Badge
    private static final Color PROGRESS_BG = new Color(230, 233, 236);
    private static final Color PROGRESS_FG = new Color(38, 92, 78);

    public RecommendedTopicsPanel(AppContext ctx, Consumer<Long> onOpenTopic) {
        this.ctx = ctx;
        this.onOpenTopic = onOpenTopic;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(28, 36, 28, 36));
        setBackground(PAGE_BG);

        // Header Title
        JLabel title = new JLabel("Trending Topics");
        title.setFont(new Font("Serif", Font.BOLD, 28));
        title.setForeground(new Color(24, 30, 38));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);

        // Content Area
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(body);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
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
        if (recs == null || recs.isEmpty()) {
            JLabel empty = new JLabel("Nothing to recommend yet \u2014 join a few discussions first.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 14));
            empty.setForeground(Color.GRAY);
            empty.setBorder(new EmptyBorder(12, 4, 12, 4));
            body.add(empty);
        } else {
            for (int i = 0; i < recs.length(); i++) {
                body.add(buildTopicCard(recs.getJSONObject(i)));
                body.add(Box.createVerticalStrut(14));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent buildTopicCard(JSONObject r) {
        JSONObject topic = r.optJSONObject("topic");
        String title = topic != null ? topic.optString("title", "Untitled topic") : r.optString("title", "Untitled topic");
        long topicId = topic != null ? topic.optLong("topic_id", -1) : r.optLong("topic_id", -1);
        double relevance = r.optDouble("relevance_score", 0);

        String category = topic != null ? topic.optString("category", "General") : "General";
        int postsCount = topic != null ? topic.optInt("posts_count", 0) : r.optInt("posts_count", 0);
        int matchPercent = (int) Math.round(relevance * 100);

        // Card Container
        JPanel card = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        // Upper Section: Title + Subtext (Left), Badge (Right)
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        // Topic Title & Stats
        JPanel textGroup = new JPanel();
        textGroup.setLayout(new BoxLayout(textGroup, BoxLayout.Y_AXIS));
        textGroup.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(TITLE_COLOR);

        JLabel subLabel = new JLabel(category + " \u00B7 " + postsCount + " posts");
        subLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLabel.setForeground(SUB_TEXT_COLOR);

        textGroup.add(titleLabel);
        textGroup.add(Box.createVerticalStrut(4));
        textGroup.add(subLabel);

        // Right-aligned % TRENDING Badge
        JLabel badge = new JLabel(matchPercent + "% TRENDING", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BADGE_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(Color.WHITE);
        badge.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.setPreferredSize(new Dimension(110, 24));

        topRow.add(textGroup, BorderLayout.WEST);
        topRow.add(badge, BorderLayout.EAST);

        // Bottom Section: Trending Progress Bar
        JComponent progressBar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Track
                g2.setColor(PROGRESS_BG);
                g2.fillRoundRect(0, 0, w, h, h, h);

                // Progress Fill
                int fillWidth = (int) (w * (matchPercent / 100.0));
                if (fillWidth > 0) {
                    g2.setColor(PROGRESS_FG);
                    g2.fillRoundRect(0, 0, fillWidth, h, h, h);
                }
                g2.dispose();
            }
        };
        progressBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 4));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));

        card.add(topRow, BorderLayout.CENTER);
        card.add(progressBar, BorderLayout.SOUTH);

        // Click Handler
        if (topicId >= 0) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onOpenTopic.accept(topicId);
                }
            });
        }

        return card;
    }
}