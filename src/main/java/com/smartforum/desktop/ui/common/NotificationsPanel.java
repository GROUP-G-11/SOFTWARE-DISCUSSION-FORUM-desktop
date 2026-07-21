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

public class NotificationsPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel body = new JPanel();

    public NotificationsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("Notifications");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);

        JButton markAll = Buttons.secondary("Mark all read");
        markAll.addActionListener(e -> markAllRead());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(markAll);
        top.add(actions, BorderLayout.EAST);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(body), BorderLayout.CENTER);
    }

    public void refresh() {
        new SwingWorker<JSONArray, Void>() {
            boolean fromCache = false;

            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listNotifications();
                    JSONArray data = response.optJSONArray("data", new JSONArray());
                    ctx.store.cacheNotifications(data);
                    return data;
                } catch (ApiException | ApiOfflineException e) {
                    fromCache = true;
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
        if (notifications.isEmpty()) {
            JLabel empty = new JLabel("You're all caught up.");
            empty.setForeground(Color.GRAY);
            body.add(empty);
        }
        for (int i = 0; i < notifications.length(); i++) {
            body.add(row(notifications.getJSONObject(i)));
            body.add(Box.createVerticalStrut(1));
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent row(JSONObject n) {
        boolean read = n.optBoolean("is_read", false);
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(read ? Theme.WHITE : Theme.SKY_DIM);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 8, 10, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel msg = new JLabel("<html><body style='width:420px'>" + n.optString("message", "") + "</body></html>");
        msg.setFont(read ? Theme.BODY_FONT : Theme.BODY_FONT_BOLD);
        JLabel time = new JLabel(n.optString("created_at", ""));
        time.setFont(Theme.SMALL_FONT);
        time.setForeground(Color.GRAY);
        left.add(msg);
        left.add(time);
        row.add(left, BorderLayout.WEST);

        if (!read) {
            JButton markRead = Buttons.secondary("Mark read");
            markRead.addActionListener(e -> markOneRead(n.optLong("notification_id", -1)));
            row.add(markRead, BorderLayout.EAST);
        }
        return row;
    }

    private void markOneRead(long id) {
        if (id < 0) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.markNotificationRead(id);
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

    private void markAllRead() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.markAllNotificationsRead();
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
