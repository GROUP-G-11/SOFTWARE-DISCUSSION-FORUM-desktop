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

/** Matches dashboard/admin.blade.php #panel-warnings: inactivity warnings, with a Resolve action. */
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

    public void refresh() {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    return ctx.api.listWarnings();
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONArray warnings;
                try {
                    warnings = get();
                } catch (Exception e) {
                    warnings = null;
                }
                render(warnings);
            }
        }.execute();
    }

    private void render(JSONArray warnings) {
        body.removeAll();
        if (warnings == null) {
            body.add(new JLabel("Warnings need an internet connection to load."));
        } else if (warnings.isEmpty()) {
            body.add(new JLabel("No inactivity warnings or flagged content right now."));
        } else {
            for (int i = 0; i < warnings.length(); i++) {
                body.add(row(warnings.getJSONObject(i)));
                body.add(Box.createVerticalStrut(1));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent row(JSONObject w) {
        JPanel r = new JPanel(new BorderLayout());
        r.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 4, 10, 4)));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        JSONObject user = w.optJSONObject("user");
        JSONObject group = w.optJSONObject("group");
        boolean resolved = w.optBoolean("resolved", false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel name = new JLabel((user != null ? user.optString("full_name") : "Member")
                + "  \u2014  warning #" + w.optInt("sequence_number", 1)
                + (group != null ? "  in " + group.optString("name") : ""));
        name.setFont(Theme.BODY_FONT_BOLD);
        JLabel date = new JLabel("Issued " + w.optString("issue_date", ""));
        date.setFont(Theme.SMALL_FONT);
        date.setForeground(Color.GRAY);
        left.add(name);
        left.add(date);

        r.add(left, BorderLayout.WEST);

        if (resolved) {
            r.add(Buttons.pill("RESOLVED", Theme.SKY_DIM, Theme.SKY), BorderLayout.EAST);
        } else {
            JButton resolve = Buttons.secondary("Resolve");
            resolve.addActionListener(e -> resolveWarning(w.getLong("warning_id")));
            r.add(resolve, BorderLayout.EAST);
        }
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
}
