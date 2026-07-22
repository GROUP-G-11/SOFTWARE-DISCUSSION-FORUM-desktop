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
 
    public NotificationsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(28, 36, 28, 36));
        setBackground(Theme.WHITE);
 
        JLabel title = new JLabel("Notifications");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);
 
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
 
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
            empty.setFont(Theme.BODY_FONT);
            empty.setForeground(Theme.MUTED);
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
 
    // Mirrors the web client's notifIconMeta() in layouts/app.blade.php —
    // same 5 categories, same fallback order (quiz checked before the
    // "general" catch-all, an exact match required for "reply" so it
    // doesn't shadow other types, etc).
    private record IconMeta(String icon, Color bg, Color fg, Color accent, String title) {
    }
 
    private IconMeta notifIconMeta(String type, String message) {
        String t = (type == null ? "" : type).toLowerCase();
        String m = (message == null ? "" : message).toLowerCase();
 
        if (t.contains("quiz")) {
            return new IconMeta("\uD83D\uDCDD", new Color(0xFEF3C7), new Color(0xB45309), new Color(0xB45309), "Quiz Announcement");
        }
        if (t.contains("blacklist")) {
            return new IconMeta("\uD83D\uDD12", new Color(0xEDE9FE), new Color(0x6D28D9), new Color(0x6D28D9), "Blacklist");
        }
        if (t.contains("warning")) {
            return new IconMeta("\u26A0", new Color(0xFFEDD5), new Color(0xC2410C), new Color(0xC2410C), "Warning");
        }
        if (t.equals("reply")) {
            return new IconMeta("\u21A9", new Color(0xDBEAFE), new Color(0x1D4ED8), new Color(0x1D4ED8), "Reply");
        }
        if (t.contains("new post")) {
            return new IconMeta("\uD83D\uDCAC", new Color(0xDBEAFE), new Color(0x1D4ED8), new Color(0x1D4ED8), "New Post");
        }
        // The web app's flag notifications are stored with type 'General'
        // (the DB enum has no 'Post Flagged'/'Reply Flagged' value), and
        // are told apart by scanning the message text — mirror that here.
        if (t.contains("general")) {
            return new IconMeta("\uD83D\uDEA9", new Color(0xFEE2E2), new Color(0xDC2626), new Color(0xDC2626),
                    m.contains("flag") ? "Flagged" : "General");
        }
        return new IconMeta("\uD83D\uDD14", Theme.PAPER_DIM, Theme.MUTED, Theme.LINE, "Notification");
    }
 
    private JComponent notificationCard(JSONObject n) {
        String message = n.optString("message", "");
        String type = n.optString("type", "");
        String createdAt = n.optString("created_at", "");
        boolean isRead = n.optBoolean("is_read", false);
        long notificationId = n.optLong("notification_id", -1);
 
        IconMeta meta = notifIconMeta(type, message);
 
        JPanel card = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isRead ? Theme.WHITE : new Color(0xFAFCFB));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(Theme.LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                // Left accent bar, colored by type, only when unread —
                // mirrors .notif-card.unread { border-left-color: ... }.
                if (!isRead) {
                    g2.setColor(meta.accent());
                    g2.fillRoundRect(0, 0, 4, getHeight() - 1, 12, 12);
                }
                g2.dispose();
            }
        };
 
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 20, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
 
        JLabel iconBadge = createIconBadge(meta);
 
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
 
        JLabel titleLabel = new JLabel(meta.title());
        titleLabel.setFont(Theme.BODY_FONT_BOLD.deriveFont(14f));
        titleLabel.setForeground(Theme.INK);
 
        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(Theme.BODY_FONT.deriveFont(13f));
        msgLabel.setForeground(Theme.MUTED);
 
        JLabel timeLabel = new JLabel(formatRelativeTime(createdAt));
        timeLabel.setFont(Theme.SMALL_FONT);
        timeLabel.setForeground(Theme.MUTED);
 
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(3));
        contentPanel.add(msgLabel);
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.add(timeLabel);
 
        card.add(iconBadge, BorderLayout.WEST);
        card.add(contentPanel, BorderLayout.CENTER);
 
        // Click-to-read, mirroring the web client's onclick="markNotificationsSeen..."
        // pattern on each .notif-card — marks just this one read, then
        // repaints so the unread dot/accent bar disappears immediately.
        if (!isRead && notificationId >= 0) {
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() {
                            try {
                                ctx.api.markNotificationRead(notificationId);
                            } catch (ApiException | ApiOfflineException ignored) {
                            }
                            return null;
                        }
 
                        @Override
                        protected void done() {
                            refresh();
                        }
                    }.execute();
                }
            });
        }
 
        return card;
    }
 
    private JLabel createIconBadge(IconMeta meta) {
        JLabel badge = new JLabel(meta.icon(), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(meta.bg());
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
 
        badge.setOpaque(false);
        badge.setFont(new Font("SansSerif", Font.PLAIN, 16));
        badge.setForeground(meta.fg());
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
            return isoDateTime;
        }
    }
}
 