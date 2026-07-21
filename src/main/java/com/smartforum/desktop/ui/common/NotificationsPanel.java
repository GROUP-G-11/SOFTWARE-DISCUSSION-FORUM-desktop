package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class NotificationsPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel body = new JPanel();

    // Theme Colors matching Laravel UI
    private static final Color PAGE_BG = Color.WHITE;
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color CARD_BORDER = new Color(230, 233, 238);
    private static final Color HEADER_TITLE = new Color(24, 30, 38);
    private static final Color NOTIF_TITLE = new Color(47, 73, 94);
    private static final Color NOTIF_BODY = new Color(80, 90, 100);
    private static final Color NOTIF_TIME = new Color(130, 140, 150);

    // Badge Colors
    private static final Color POST_ICON_BG = new Color(243, 240, 248);
    private static final Color POST_ICON_FG = new Color(125, 95, 170);

    private static final Color QUIZ_ICON_BG = new Color(253, 242, 238);
    private static final Color QUIZ_ICON_FG = new Color(220, 95, 60);

    public NotificationsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(28, 36, 28, 36));
        setBackground(PAGE_BG);

        // Header Title
        JLabel title = new JLabel("Notifications");
        title.setFont(new Font("Serif", Font.BOLD, 28));
        title.setForeground(HEADER_TITLE);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);

        // Notifications List Layout
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(body);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void refresh() {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listNotifications();
                    JSONArray data = response.optJSONArray("data", new JSONArray());
                    ctx.store.cacheNotifications(data);
                    return data;
                } catch (ApiException | ApiOfflineException e) {
                    JSONArray arr = new JSONArray();
                    ctx.store.cachedNotifications().forEach(arr::put);
                    return arr;
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

    private void render(JSONArray notifications) {
        body.removeAll();
        if (notifications == null || notifications.isEmpty()) {
            JLabel empty = new JLabel("You're all caught up.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 14));
            empty.setForeground(Color.GRAY);
            empty.setBorder(new EmptyBorder(12, 4, 12, 4));
            body.add(empty);
        } else {
            for (int i = 0; i < notifications.length(); i++) {
                body.add(notificationCard(notifications.getJSONObject(i)));
                body.add(Box.createVerticalStrut(12));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent notificationCard(JSONObject n) {
        String message = n.optString("message", "");
        String type = n.optString("type", "");
        String createdAt = n.optString("created_at", "");

        // Determine if notification is a quiz or a post
        boolean isQuiz = type.equalsIgnoreCase("quiz") || message.toLowerCase().contains("quiz");

        // Outer Card Container with Rounded Borders
        JPanel card = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        // Circular Icon Badge (Left)
        JLabel iconBadge = createIconBadge(isQuiz);

        // Content Area (Center)
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(isQuiz ? "Quiz Announcement" : "New Post");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setForeground(NOTIF_TITLE);

        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        msgLabel.setForeground(NOTIF_BODY);

        JLabel timeLabel = new JLabel(formatRelativeTime(createdAt));
        timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timeLabel.setForeground(NOTIF_TIME);

        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(3));
        contentPanel.add(msgLabel);
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.add(timeLabel);

        card.add(iconBadge, BorderLayout.WEST);
        card.add(contentPanel, BorderLayout.CENTER);

        return card;
    }

    private JLabel createIconBadge(boolean isQuiz) {
        String iconSymbol = isQuiz ? "📝" : "💬";
        Color bg = isQuiz ? QUIZ_ICON_BG : POST_ICON_BG;
        Color fg = isQuiz ? QUIZ_ICON_FG : POST_ICON_FG;

        JLabel badge = new JLabel(iconSymbol, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };

        badge.setOpaque(false);
        badge.setFont(new Font("SansSerif", Font.PLAIN, 16));
        badge.setForeground(fg);
        badge.setPreferredSize(new Dimension(42, 42));
        badge.setMaximumSize(new Dimension(42, 42));

        return badge;
    }

    private String formatRelativeTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) return "";
        try {
            Instant created = Instant.parse(isoDateTime);
            Instant now = Instant.now();
            Duration duration = Duration.between(created, now);

            long seconds = Math.max(0, duration.getSeconds());
            if (seconds < 60) return seconds + "s ago";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m ago";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            return days + "d ago";
        } catch (DateTimeParseException e) {
            return isoDateTime; // Fallback to raw string if format varies
        }
    }

    // -----------------------------------------------------------------
    // Helper Red Notification Badge for Sidebar Navigation
    // -----------------------------------------------------------------
    public static class SidebarNotificationBadge extends JLabel {
        public SidebarNotificationBadge(int count) {
            super(count > 9 ? "9+" : String.valueOf(count), SwingConstants.CENTER);
            setFont(new Font("SansSerif", Font.BOLD, 11));
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(24, 18));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(220, 53, 69)); // Laravel Alert Red
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}