package com.smartforum.desktop.ui.admin;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import com.smartforum.desktop.ui.common.Buttons;
import com.smartforum.desktop.ui.common.Theme;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class WarningsPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel body = new JPanel();

    public WarningsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("Inactivity Warnings and Flags");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);
        JLabel sub = new JLabel("Inactivity warnings and content flagged by lecturers or student group admins, most recent first.");
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

    /** One row of merged data, either an inactivity warning or a flagged notification. */
    private record Entry(boolean isWarning, JSONObject data, long sortValue) {
    }

    public void refresh() {
        new SwingWorker<List<Entry>, Void>() {
            @Override
            protected List<Entry> doInBackground() {
                JSONArray warnings = null;
                JSONArray notifications = null;
                try {
                    warnings = ctx.api.listWarnings();
                } catch (ApiException | ApiOfflineException ignored) {
                }
                try {
                    JSONObject notifResp = ctx.api.listNotifications();
                    notifications = notifResp.optJSONArray("data", null);
                    if (notifications == null) {
                        // Some endpoints return a bare array instead of {"data": [...]}.
                        notifications = new JSONArray();
                    }
                } catch (ApiException | ApiOfflineException ignored) {
                }

                if (warnings == null && notifications == null) {
                    return null;
                }

                List<Entry> entries = new ArrayList<>();
                if (warnings != null) {
                    for (int i = 0; i < warnings.length(); i++) {
                        JSONObject w = warnings.getJSONObject(i);
                        entries.add(new Entry(true, w, parseTime(w.optString("issue_date", ""))));
                    }
                }
                if (notifications != null) {
                    for (int i = 0; i < notifications.length(); i++) {
                        JSONObject n = notifications.getJSONObject(i);
                        if (isUnreadFlag(n)) {
                            entries.add(new Entry(false, n, parseTime(n.optString("created_at", ""))));
                        }
                    }
                }
                entries.sort((a, b) -> Long.compare(b.sortValue(), a.sortValue()));
                return entries;
            }

            @Override
            protected void done() {
                List<Entry> entries;
                try {
                    entries = get();
                } catch (Exception e) {
                    entries = null;
                }
                render(entries);
            }
        }.execute();
    }

    private boolean isUnreadFlag(JSONObject n) {
        Object raw = n.opt("is_read");
        boolean isRead = "true".equals(String.valueOf(raw)) || "1".equals(String.valueOf(raw)) || Boolean.TRUE.equals(raw);
        if (isRead) return false;

        String type = n.optString("type", "").toLowerCase();
        String message = n.optString("message", "").toLowerCase();
        return type.contains("flag") || message.contains("flag");
    }

    private long parseTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) return 0;
        try {
            return Instant.parse(isoDateTime).toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private void render(List<Entry> entries) {
        body.removeAll();
        if (entries == null) {
            body.add(new JLabel("Warnings need an internet connection to load."));
        } else if (entries.isEmpty()) {
            body.add(new JLabel("No inactivity warnings or flagged content right now."));
        } else {
            for (Entry entry : entries) {
                body.add(entry.isWarning() ? warningRow(entry.data()) : flagRow(entry.data()));
                body.add(Box.createVerticalStrut(1));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent warningRow(JSONObject w) {
        JSONObject user = w.optJSONObject("user");
        JSONObject group = w.optJSONObject("group");
        boolean resolved = w.optBoolean("resolved", false);

        JComponent right;
        if (resolved) {
            right = Buttons.pill("RESOLVED", Theme.SKY_DIM, Theme.SKY);
        } else {
            JButton resolve = Buttons.secondary("Resolve");
            resolve.addActionListener(e -> resolveWarning(w.getLong("warning_id")));
            right = resolve;
        }

        String name = (user != null ? user.optString("full_name") : "Member")
                + "  \u2014  warning #" + w.optInt("sequence_number", 1)
                + (group != null ? "  in " + group.optString("name") : "");

        return row(Buttons.pill("INACTIVITY", new Color(0xFFEDD5), new Color(0xC2410C)),
                name, "Issued " + w.optString("issue_date", ""), right);
    }

    private JComponent flagRow(JSONObject n) {
        long notificationId = n.optLong("notification_id", n.optLong("id", -1));
        // The Post vs Reply distinction lives in related_type (a plain,
        // unconstrained string column) rather than the notifications.type
        // ENUM, which only ever stores 'General' for these - see
        // PostController::flag()/ReplyController::flag() on the backend.
        String kind = n.optString("related_type", "Content");
        String message = n.optString("message", "");

        JButton dismiss = Buttons.secondary("Dismiss");
        dismiss.addActionListener(e -> dismissFlag(notificationId, dismiss));

        return row(Buttons.pill((kind + " FLAGGED").toUpperCase(), new Color(0xFEE2E2), new Color(0xDC2626)),
                message, formatDate(n.optString("created_at", "")), dismiss);
    }

    private String formatDate(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) return "N/A";
        return isoDateTime;
    }

    private JComponent row(JComponent badge, String title, String subtitle, JComponent action) {
        JPanel r = new JPanel(new BorderLayout(12, 0));
        r.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 4, 10, 4)));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(Theme.BODY_FONT_BOLD);
        JLabel date = new JLabel(subtitle);
        date.setFont(Theme.SMALL_FONT);
        date.setForeground(Color.GRAY);
        left.add(name);
        left.add(date);

        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.setOpaque(false);
        center.add(badgeWrap, BorderLayout.WEST);
        center.add(left, BorderLayout.CENTER);

        r.add(center, BorderLayout.CENTER);
        r.add(action, BorderLayout.EAST);
        return r;
    }

    private void resolveWarning(long warningId) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.resolveWarning(warningId);
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

    private void dismissFlag(long notificationId, JButton source) {
        if (notificationId < 0) return;
        source.setEnabled(false);
        source.setText("Dismissing\u2026");
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
}
