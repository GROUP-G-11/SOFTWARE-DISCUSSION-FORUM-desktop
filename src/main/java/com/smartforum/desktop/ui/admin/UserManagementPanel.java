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

public class UserManagementPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel body = new JPanel();
    private final JTextField searchField = new JTextField();

    public UserManagementPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("User Management");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);

        searchField.setColumns(20);
        JButton searchBtn = Buttons.secondary("Search");
        searchBtn.addActionListener(e -> refresh());
        searchField.addActionListener(e -> refresh());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchField);
        searchRow.add(searchBtn);
        top.add(searchRow, BorderLayout.EAST);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(body), BorderLayout.CENTER);
    }

    public void refresh() {
        String search = searchField.getText();
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listUsers(search);
                    return response.optJSONArray("data", new JSONArray());
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONArray users;
                try {
                    users = get();
                } catch (Exception e) {
                    users = null;
                }
                render(users);
            }
        }.execute();
    }

    private void render(JSONArray users) {
        body.removeAll();
        if (users == null) {
            body.add(new JLabel("User management needs an internet connection."));
        } else {
            for (int i = 0; i < users.length(); i++) {
                body.add(userRow(users.getJSONObject(i)));
                body.add(Box.createVerticalStrut(1));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent userRow(JSONObject user) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 4, 10, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(user.optString("full_name", "User"));
        name.setFont(Theme.BODY_FONT_BOLD);
        JLabel email = new JLabel(user.optString("email", ""));
        email.setFont(Theme.SMALL_FONT);
        email.setForeground(Color.GRAY);
        left.add(name);
        left.add(email);

        String currentRole = "Student";
        JSONArray roles = user.optJSONArray("roles");
        if (roles != null && !roles.isEmpty()) {
            currentRole = roles.getJSONObject(0).optString("role_name", "Student");
        }

        JComboBox<String> roleBox = new JComboBox<>(new String[]{"Student", "Lecturer", "Administrator"});
        roleBox.setSelectedItem(currentRole);

        JButton applyBtn = Buttons.secondary("Apply");
        applyBtn.addActionListener(e -> assignRole(user.getLong("user_id"), (String) roleBox.getSelectedItem()));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(roleBox);
        right.add(applyBtn);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void assignRole(long userId, String role) {
        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.assignRole(userId, role);
                } catch (ApiOfflineException e) {
                    error = "Changing a role needs an internet connection.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(UserManagementPanel.this, error, "Couldn't update role", JOptionPane.WARNING_MESSAGE);
                } else {
                    refresh();
                }
            }
        }.execute();
    }
}
