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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class UserManagementPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel body = new JPanel();
    private final JTextField searchField = new JTextField();

    // Theme Colors for Badges matching Web Design
    private static final Color ROLE_ADMIN_BG = new Color(253, 237, 230);
    private static final Color ROLE_ADMIN_FG = new Color(224, 86, 36);

    private static final Color ROLE_STUDENT_BG = new Color(236, 243, 240);
    private static final Color ROLE_STUDENT_FG = new Color(47, 125, 96);

    private static final Color ROLE_LECTURER_BG = new Color(234, 240, 250);
    private static final Color ROLE_LECTURER_FG = new Color(66, 115, 212);

    private static final Color PRIMARY_GREEN = new Color(30, 107, 82);

    public UserManagementPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(28, 36, 28, 36));
        setBackground(Color.WHITE);

        // Header Title matching Web UI ("Manage Users")
        JLabel title = new JLabel("Manage Users");
        title.setFont(new Font("Serif", Font.BOLD, 28));
        title.setForeground(new Color(24, 30, 38));

        // Full-width Search Bar Container
        JPanel searchContainer = new JPanel(new BorderLayout());
        searchContainer.setOpaque(false);

        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Placeholder logic
        searchField.setText("Search by name or email...");
        searchField.setForeground(Color.GRAY);
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (searchField.getText().equals("Search by name or email...")) {
                    searchField.setText("");
                    searchField.setForeground(Color.BLACK);
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (searchField.getText().trim().isEmpty()) {
                    searchField.setText("Search by name or email...");
                    searchField.setForeground(Color.GRAY);
                }
            }
        });

        // Trigger search dynamically while typing
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { triggerSearch(); }
            public void removeUpdate(DocumentEvent e) { triggerSearch(); }
            public void insertUpdate(DocumentEvent e) { triggerSearch(); }

            private void triggerSearch() {
                if (!searchField.getText().equals("Search by name or email...")) {
                    refresh();
                }
            }
        });

        searchContainer.add(searchField, BorderLayout.CENTER);

        JPanel topSection = new JPanel(new BorderLayout(0, 16));
        topSection.setOpaque(false);
        topSection.add(title, BorderLayout.NORTH);
        topSection.add(searchContainer, BorderLayout.SOUTH);

        // Setup Main Body Layout
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(body);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(230, 233, 238), 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(topSection, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        refresh();

    }

    public void refresh() {
        String search = searchField.getText();
        if ("Search by name or email...".equals(search)) {
            search = "";
        }

        final String query = search;
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listUsers(query);
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
            JLabel err = new JLabel("User management needs an internet connection.");
            err.setBorder(new EmptyBorder(16, 16, 16, 16));
            body.add(err);
        } else {
            // Render Table Header
            body.add(tableHeader());

            // Render Table Rows
            for (int i = 0; i < users.length(); i++) {
                body.add(userRow(users.getJSONObject(i)));
            }
        }

        body.revalidate();
        body.repaint();
    }

    private JComponent tableHeader() {
        JPanel header = new JPanel(new GridLayout(1, 4, 16, 0));
        header.setOpaque(true);
        header.setBackground(new Color(250, 251, 252));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 229, 235)),
                new EmptyBorder(12, 16, 12, 16)
        ));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        header.add(createHeaderLabel("Name"));
        header.add(createHeaderLabel("Email"));
        header.add(createHeaderLabel("Current role(s)"));
        header.add(createHeaderLabel("Assign role"));

        return header;
    }

    private JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(new Color(60, 66, 74));
        return label;
    }

    private JComponent userRow(JSONObject user) {
        JPanel row = new JPanel(new GridLayout(1, 4, 16, 0));
        row.setOpaque(true);
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 238, 242)),
                new EmptyBorder(14, 16, 14, 16)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));

        // Column 1: Name
        JLabel name = new JLabel(user.optString("full_name", "User"));
        name.setFont(new Font("SansSerif", Font.PLAIN, 13));
        name.setForeground(new Color(40, 44, 52));

        // Column 2: Email
        JLabel email = new JLabel(user.optString("email", ""));
        email.setFont(new Font("SansSerif", Font.PLAIN, 13));
        email.setForeground(new Color(40, 44, 52));

        // Column 3: Current Roles (Badges)
        JPanel rolesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rolesPanel.setOpaque(false);

        JSONArray roles = user.optJSONArray("roles");
        if (roles == null || roles.isEmpty()) {
            JLabel noneLabel = new JLabel("None");
            noneLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
            noneLabel.setForeground(Color.GRAY);
            rolesPanel.add(noneLabel);
        } else {
            for (int i = 0; i < roles.length(); i++) {
                String roleName = roles.getJSONObject(i).optString("role_name", "").toUpperCase();
                rolesPanel.add(createRoleBadge(roleName));
            }
        }

        // Column 4: Assign Role Controls (ComboBox + Assign Button)
        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));
        actionPanel.setOpaque(false);

        String currentRole = "Student";
        if (roles != null && !roles.isEmpty()) {
            currentRole = roles.getJSONObject(0).optString("role_name", "Student");
            // Normalize casing
            currentRole = currentRole.substring(0, 1).toUpperCase() + currentRole.substring(1).toLowerCase();
        }

        JComboBox<String> roleBox = new JComboBox<>(new String[]{"Student", "Lecturer", "Administrator"});
        roleBox.setSelectedItem(currentRole);
        roleBox.setMaximumSize(new Dimension(160, 28));
        roleBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton assignBtn = new JButton("Assign");
        assignBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        assignBtn.setForeground(Color.WHITE);
        assignBtn.setBackground(PRIMARY_GREEN);
        assignBtn.setFocusPainted(false);
        assignBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
        assignBtn.setMaximumSize(new Dimension(80, 26));
        assignBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        assignBtn.addActionListener(e -> assignRole(user.getLong("user_id"), (String) roleBox.getSelectedItem()));

        actionPanel.add(roleBox);
        actionPanel.add(Box.createVerticalStrut(6));
        actionPanel.add(assignBtn);

        row.add(name);
        row.add(email);
        row.add(rolesPanel);
        row.add(actionPanel);

        return row;
    }

    private JLabel createRoleBadge(String roleName) {
        JLabel badge = new JLabel(roleName) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };

        badge.setOpaque(false);
        badge.setFont(new Font("SansSerif", Font.BOLD, 10));
        badge.setBorder(new EmptyBorder(3, 8, 3, 8));

        switch (roleName.toUpperCase()) {
            case "ADMINISTRATOR":
            case "ADMIN":
                badge.setBackground(ROLE_ADMIN_BG);
                badge.setForeground(ROLE_ADMIN_FG);
                break;
            case "LECTURER":
                badge.setBackground(ROLE_LECTURER_BG);
                badge.setForeground(ROLE_LECTURER_FG);
                break;
            default: // STUDENT / default
                badge.setBackground(ROLE_STUDENT_BG);
                badge.setForeground(ROLE_STUDENT_FG);
                break;
        }

        return badge;
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