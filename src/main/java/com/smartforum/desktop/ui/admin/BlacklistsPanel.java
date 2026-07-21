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

/** Matches dashboard/admin.blade.php #panel-blacklists: active suspensions, with a Lift action. */
public class BlacklistsPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel body = new JPanel();

    public BlacklistsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("Blacklisted Users");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);
        JLabel sub = new JLabel("<html><body style='width:600px'>Currently-active suspensions. \"Whole account\" bans (issued for "
                + "prolonged inactivity) block login entirely; group bans only block that one group. Lift ends a "
                + "suspension immediately.</body></html>");
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
                    return ctx.api.listBlacklists();
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONArray blacklists;
                try {
                    blacklists = get();
                } catch (Exception e) {
                    blacklists = null;
                }
                render(blacklists);
            }
        }.execute();
    }

    private void render(JSONArray blacklists) {
        body.removeAll();
        if (blacklists == null) {
            body.add(new JLabel("Blacklists need an internet connection to load."));
        } else if (blacklists.isEmpty()) {
            body.add(new JLabel("No one is currently blacklisted."));
        } else {
            for (int i = 0; i < blacklists.length(); i++) {
                body.add(row(blacklists.getJSONObject(i)));
                body.add(Box.createVerticalStrut(1));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent row(JSONObject b) {
        JPanel r = new JPanel(new BorderLayout());
        r.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 4, 10, 4)));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        JSONObject user = b.optJSONObject("user");
        JSONObject group = b.optJSONObject("group");

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel name = new JLabel((user != null ? user.optString("full_name") : "Member")
                + (group != null ? "  \u2014  " + group.optString("name") : ""));
        name.setFont(Theme.BODY_FONT_BOLD);
        JLabel meta = new JLabel("Reason: " + b.optString("reason", "manual") + "  \u00b7  ends " + b.optString("end_date", ""));
        meta.setFont(Theme.SMALL_FONT);
        meta.setForeground(Color.GRAY);
        left.add(name);
        left.add(meta);

        JButton lift = Buttons.secondary("Lift");
        lift.addActionListener(e -> liftBlacklist(b.getLong("blacklist_id")));

        r.add(left, BorderLayout.WEST);
        r.add(lift, BorderLayout.EAST);
        return r;
    }

    private void liftBlacklist(long blacklistId) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.liftBlacklist(blacklistId);
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
