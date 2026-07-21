package com.smartforum.desktop.ui;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import com.smartforum.desktop.ui.common.Theme;

import javax.swing.*;
import java.awt.*;

public class RegisterPanel extends JPanel {

    private final AppWindow app;
    private final AppContext ctx;
    private final JTextField nameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();
    private final JCheckBox rulesCheckbox = new JCheckBox("I agree to the forum rules and guidelines");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton registerBtn = new JButton("Register now");

    public RegisterPanel(AppWindow app, AppContext ctx) {
        this.app = app;
        this.ctx = ctx;
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Theme.INK);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.PAPER);
        card.setOpaque(true);
        card.setBorder(BorderFactory.createEmptyBorder(30, 45, 30, 45));
        card.setPreferredSize(new Dimension(560, 700));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Create your account");
        title.setFont(Theme.AUTH_TITLE_FONT.deriveFont(26f));
        title.setForeground(Theme.INK);
        card.add(title, gc);

        gc.gridy++;
        gc.insets = new Insets(6, 0, 0, 0);
        JLabel subtitle = new JLabel("Join the Smart Discussion Forum");
        subtitle.setFont(Theme.AUTH_SUBTITLE_FONT);
        subtitle.setForeground(Theme.MUTED);
        card.add(subtitle, gc);

        errorLabel.setFont(Theme.BODY_FONT_BOLD.deriveFont(12f));
        errorLabel.setForeground(Theme.WARN);
        gc.gridy++;
        gc.insets = new Insets(8, 0, 2, 0);
        card.add(errorLabel, gc);

        nameField.setPreferredSize(new Dimension(0, 34));
        emailField.setPreferredSize(new Dimension(0, 34));
        passwordField.setPreferredSize(new Dimension(0, 34));
        confirmField.setPreferredSize(new Dimension(0, 34));
        nameField.setFont(Theme.BODY_FONT);
        emailField.setFont(Theme.BODY_FONT);
        passwordField.setFont(Theme.BODY_FONT);
        confirmField.setFont(Theme.BODY_FONT);

        gc.insets = new Insets(10, 0, 2, 0);
        addField(card, gc, "Full name", nameField);
        addField(card, gc, "Email", emailField);
        addField(card, gc, "Password", passwordField);
        addField(card, gc, "Confirm password", confirmField);

        // Role note - every account starts as a Student; becoming a
        // Lecturer is an Administrator action (Role Management, SDD Table
        // 30), never a self-service choice at sign-up.
        gc.gridy++;
        gc.gridwidth = 2;
        gc.insets = new Insets(14, 0, 8, 0);
        card.add(infoBox("Every new account starts as a Student. If you're a lecturer, contact "
                + "your system administrator after registering and they'll assign your account the Lecturer role."), gc);

        // Rules acceptance - On-boarding use case (SDD Table 25): shown
        // inline rather than as a separate popup, matching the web client.
        gc.gridy++;
        gc.insets = new Insets(8, 0, 8, 0);
        card.add(infoBox("By joining, you agree to keep discussion on-topic, avoid flooding threads with "
                + "irrelevant material, respect selective-communication exclusions set by other members, and "
                + "understand that prolonged inactivity may result in warnings and temporary suspension."), gc);

        gc.gridy++;
        gc.insets = new Insets(4, 0, 8, 0);
        rulesCheckbox.setFont(Theme.BODY_FONT_BOLD.deriveFont(13f));
        rulesCheckbox.setForeground(Theme.MUTED.darker());
        rulesCheckbox.setOpaque(false);
        rulesCheckbox.setFocusPainted(false);
        rulesCheckbox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(rulesCheckbox, gc);

        gc.gridy++;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.NONE;
        gc.insets = new Insets(14, 0, 8, 0);

        registerBtn.setPreferredSize(new Dimension(160, 38));
        registerBtn.setBackground(Theme.ACCENT);
        registerBtn.setForeground(Theme.WHITE);
        registerBtn.setFont(Theme.BODY_FONT_BOLD.deriveFont(15f));
        registerBtn.setFocusPainted(false);
        registerBtn.setOpaque(true);
        registerBtn.setBorderPainted(false);
        registerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(registerBtn, gc);

        gc.gridy++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        JButton toLogin = new JButton("Already registered? Log in");
        toLogin.setBorderPainted(false);
        toLogin.setContentAreaFilled(false);
        toLogin.setFont(Theme.BODY_FONT_BOLD.deriveFont(13f));
        toLogin.setForeground(Theme.ACCENT);
        toLogin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gc.insets = new Insets(2, 0, 0, 0);
        card.add(toLogin, gc);

        GridBagConstraints center = new GridBagConstraints();
        center.gridx = 0;
        center.gridy = 0;
        add(card, center);

        registerBtn.addActionListener(e -> doRegister());
        toLogin.addActionListener(e -> {
            errorLabel.setText(" ");
            app.showCard(AppWindow.CARD_LOGIN);
        });
    }

    private void addField(JPanel card, GridBagConstraints gc, String labelText, JComponent field) {
        gc.gridy++;
        gc.gridwidth = 1;
        JLabel label = new JLabel(labelText);
        label.setFont(Theme.FIELD_LABEL_FONT.deriveFont(13f));
        label.setForeground(Theme.MUTED.darker());
        card.add(label, gc);

        gc.gridy++;
        gc.gridwidth = 2;
        card.add(field, gc);
    }

    private JComponent infoBox(String text) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(Theme.PAPER_DIM);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JTextArea text_ = new JTextArea(text);
        text_.setFont(Theme.SMALL_FONT.deriveFont(12.5f));
        text_.setForeground(Theme.MUTED.darker());
        text_.setBackground(Theme.PAPER_DIM);
        text_.setLineWrap(true);
        text_.setWrapStyleWord(true);
        text_.setEditable(false);
        text_.setFocusable(false);

        box.add(text_, BorderLayout.CENTER);
        return box;
    }

    private void doRegister() {
        errorLabel.setText(" ");

        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            errorLabel.setText("Fill in every field.");
            return;
        }
        if (!password.equals(confirm)) {
            errorLabel.setText("Passwords do not match.");
            return;
        }
        if (password.length() < 8) {
            errorLabel.setText("Password must be at least 8 characters.");
            return;
        }
        if (!rulesCheckbox.isSelected()) {
            errorLabel.setText("You must agree to the forum rules and guidelines.");
            return;
        }

        registerBtn.setEnabled(false);
        registerBtn.setText("Processing\u2026");

        new SwingWorker<Boolean, Void>() {
            String error = null;

            @Override
            protected Boolean doInBackground() {
                try {
                    // No role is sent: every new account starts as a
                    // Student server-side (AuthController defaults to it),
                    // matching the web client's registration form.
                    ctx.auth.register(name, email, password, confirm, null);
                    return true;
                } catch (ApiOfflineException e) {
                    error = "Creating an account needs an internet connection.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return false;
            }

            @Override
            protected void done() {
                registerBtn.setEnabled(true);
                registerBtn.setText("Register now");

                boolean ok;
                try {
                    ok = get();
                } catch (Exception e) {
                    ok = false;
                }
                if (!ok) {
                    errorLabel.setText(error != null ? error : "Registration failed.");
                    return;
                }
                passwordField.setText("");
                confirmField.setText("");
                rulesCheckbox.setSelected(false);
                app.onAuthenticated();
            }
        }.execute();
    }
}
