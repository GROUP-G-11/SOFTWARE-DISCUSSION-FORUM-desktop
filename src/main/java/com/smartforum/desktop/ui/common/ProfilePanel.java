package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-service profile editor docked cleanly at the top-center
 * without vertical stretching or scrollbar clipping.
 */
public class ProfilePanel extends JPanel {

    private final AppContext ctx;
    private final JLabel avatarCircle = new JLabel("", SwingConstants.CENTER);
    private final JLabel nameLabel = new JLabel();
    private final JLabel roleBadge = new JLabel();
    private final JTextField nameField = new JTextField(30);
    private final JTextArea bioField = new JTextArea(3, 30);
    private final JTextField phoneField = new JTextField(30);
    private final JCheckBox phonePublicBox = new JCheckBox("Show my phone number to other members");
    private final JTextField departmentField = new JTextField(30);
    private final JLabel status = new JLabel(" ");

    public ProfilePanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new GridBagLayout());
        setBackground(Theme.WHITE);

        // Profile Card
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Theme.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1),
                new EmptyBorder(20, 24, 20, 24)));

        card.add(buildHeader(), BorderLayout.NORTH);
        card.add(buildForm(), BorderLayout.CENTER);
        card.add(buildFooter(), BorderLayout.SOUTH);

        // Dock Card to TOP-CENTER with natural height
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTH; // Docks at top
        gbc.weighty = 1.0;                     // Pushes extra vertical space downwards
        gbc.insets = new Insets(20, 0, 20, 0);

        add(card, gbc);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        avatarCircle.setOpaque(true);
        avatarCircle.setBackground(Theme.ACCENT);
        avatarCircle.setForeground(Theme.WHITE);
        avatarCircle.setFont(Theme.HEADING_FONT.deriveFont(22f));
        avatarCircle.setPreferredSize(new Dimension(64, 64));
        avatarCircle.setMaximumSize(new Dimension(64, 64));
        avatarCircle.setAlignmentX(Component.CENTER_ALIGNMENT);
        avatarCircle.setBorder(BorderFactory.createLineBorder(Theme.ACCENT_DARK, 2, true));

        nameLabel.setFont(Theme.HEADING_FONT_SM);
        nameLabel.setForeground(Theme.INK);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        roleBadge.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        roleBadge.setForeground(Theme.ACCENT_DARK);
        roleBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(avatarCircle);
        header.add(Box.createVerticalStrut(6));
        header.add(nameLabel);
        header.add(Box.createVerticalStrut(2));
        header.add(roleBadge);
        return header;
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 0, 2, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0;
        gc.weightx = 1.0;
        gc.gridy = 0;

        nameField.setPreferredSize(new Dimension(480, 32));
        phoneField.setPreferredSize(new Dimension(480, 32));
        departmentField.setPreferredSize(new Dimension(480, 32));

        nameField.setFont(Theme.BODY_FONT);
        phoneField.setFont(Theme.BODY_FONT);
        departmentField.setFont(Theme.BODY_FONT);

        bioField.setFont(Theme.BODY_FONT);
        bioField.setLineWrap(true);
        bioField.setWrapStyleWord(true);

        JScrollPane bioScroll = new JScrollPane(bioField);
        bioScroll.setPreferredSize(new Dimension(480, 65));

        addField(form, gc, "Full name", nameField);
        gc.gridy++;
        form.add(fieldLabel("Bio"), gc);
        gc.gridy++;
        form.add(bioScroll, gc);
        gc.gridy++;
        addField(form, gc, "Phone", phoneField);
        gc.gridy++;
        phonePublicBox.setOpaque(false);
        phonePublicBox.setFont(Theme.SMALL_FONT);
        phonePublicBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        form.add(phonePublicBox, gc);
        gc.gridy++;
        addField(form, gc, "Department", departmentField);

        return form;
    }

    private void addField(JPanel form, GridBagConstraints gc, String label, JComponent field) {
        gc.gridy++;
        form.add(fieldLabel(label), gc);
        gc.gridy++;
        form.add(field, gc);
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FIELD_LABEL_FONT.deriveFont(12f));
        l.setForeground(Theme.MUTED.darker());
        return l;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 0, 0, 0));

        JButton saveBtn = Buttons.primary("Save changes");
        saveBtn.addActionListener(e -> save());

        status.setForeground(Theme.ACCENT);
        status.setFont(Theme.SMALL_FONT);

        footer.add(saveBtn, BorderLayout.WEST);
        footer.add(status, BorderLayout.EAST);
        return footer;
    }

    public void refresh() {
        JSONObject user = ctx.session.user();
        if (user == null) return;
        String fullName = user.optString("full_name", "");
        nameField.setText(fullName);
        bioField.setText(user.optString("bio", ""));
        phoneField.setText(user.optString("phone", ""));
        phonePublicBox.setSelected(user.optBoolean("phone_public", false));
        departmentField.setText(user.optString("department", ""));

        nameLabel.setText(fullName.isBlank() ? "Your profile" : fullName);
        roleBadge.setText(ctx.session.primaryRole().toUpperCase());
        avatarCircle.setText(initials(fullName));
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() > 0 ? sb.toString() : "?";
    }

    private void save() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("full_name", nameField.getText().trim());
        fields.put("bio", bioField.getText().trim());
        fields.put("phone", phoneField.getText().trim());
        fields.put("phone_public", phonePublicBox.isSelected());
        fields.put("department", departmentField.getText().trim());

        new SwingWorker<JSONObject, Void>() {
            String error = null;

            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.updateProfile(fields);
                } catch (ApiOfflineException e) {
                    error = "Saving your profile needs an internet connection.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    status.setForeground(Theme.WARN);
                    status.setText(error);
                    return;
                }
                try {
                    JSONObject response = get();
                    ctx.session.set(response.getJSONObject("user"), ctx.session.token(), ctx.session.isOfflineMode());
                    refresh();
                } catch (Exception ignored) {
                }
                status.setForeground(Theme.ACCENT);
                status.setText("Saved.");
            }
        }.execute();
    }
}