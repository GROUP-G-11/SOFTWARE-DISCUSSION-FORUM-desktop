package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class GroupMembersDialog extends JDialog {

    public GroupMembersDialog(Window owner, AppContext ctx, long groupId, String groupName) {
        super(owner, (groupName == null ? "Group" : groupName) + " members", ModalityType.APPLICATION_MODAL);
        setResizable(false);
        getContentPane().setBackground(Theme.WHITE);
        setLayout(new BorderLayout());

        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(Theme.WHITE);
        card.setBorder(new EmptyBorder(24, 26, 20, 26));

        JLabel title = new JLabel((groupName == null ? "Group" : groupName) + " members");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);
        card.add(title, BorderLayout.NORTH);

        JPanel listBody = new JPanel();
        listBody.setLayout(new BoxLayout(listBody, BoxLayout.Y_AXIS));
        listBody.setBackground(Theme.WHITE);

        JLabel loading = new JLabel("Loading\u2026");
        loading.setFont(Theme.BODY_FONT);
        loading.setForeground(Theme.MUTED);
        listBody.add(loading);

        JScrollPane scroll = new JScrollPane(listBody);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(380, 300));
        card.add(scroll, BorderLayout.CENTER);

        JButton close = Buttons.secondary("Close");
        close.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(close);
        card.add(footer, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
        setSize(440, 420);
        setLocationRelativeTo(owner);   // <-- centers this popup too

        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.groupMembers(groupId);
                    JSONArray data = response.optJSONArray("data", null);
                    return data != null ? data : new JSONArray();
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONArray members;
                try {
                    members = get();
                } catch (Exception e) {
                    members = null;
                }
                listBody.removeAll();
                if (members == null) {
                    listBody.add(memberRow("Members list needs an internet connection.", null));
                } else if (members.isEmpty()) {
                    listBody.add(memberRow("No members yet.", null));
                } else {
                    for (int i = 0; i < members.length(); i++) {
                        JSONObject m = members.getJSONObject(i);
                        listBody.add(memberRow(m.optString("full_name", "Member"), m.optString("role", null)));
                    }
                }
                listBody.revalidate();
                listBody.repaint();
            }
        }.execute();
    }

    private JComponent memberRow(String name, String role) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(Theme.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(10, 4, 10, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(Theme.BODY_FONT_BOLD);
        nameLabel.setForeground(Theme.SKY);
        row.add(nameLabel, BorderLayout.WEST);

        if (role != null && !role.isBlank()) {
            JLabel roleLabel = new JLabel(role);
            roleLabel.setFont(Theme.SMALL_FONT);
            roleLabel.setForeground(Theme.MUTED);
            row.add(roleLabel, BorderLayout.EAST);
        }
        return row;
    }
}