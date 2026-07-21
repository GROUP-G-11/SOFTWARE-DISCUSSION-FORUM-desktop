package com.smartforum.desktop.ui;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.auth.AuthService;
import com.smartforum.desktop.ui.common.Theme;

import javax.swing.*;
import java.awt.*;

public class LoginPanel extends JPanel {

    private final AppWindow app;
    private final AppContext ctx;
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton loginBtn = new JButton("Login");

    public LoginPanel(AppWindow app, AppContext ctx) {
        this.app = app;
        this.ctx = ctx;
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Theme.INK);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.PAPER);
        card.setOpaque(true);
        card.setBorder(BorderFactory.createEmptyBorder(45, 45, 45, 45));
        card.setPreferredSize(new Dimension(460, 520));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Welcome back");
        title.setFont(Theme.AUTH_TITLE_FONT);
        title.setForeground(Theme.INK);
        card.add(title, gc);

        gc.gridy++;
        gc.insets = new Insets(6, 0, 0, 0);
        JLabel subtitle = new JLabel("Log in to your discussion forum account.");
        subtitle.setFont(Theme.AUTH_SUBTITLE_FONT);
        subtitle.setForeground(Theme.MUTED);
        card.add(subtitle, gc);

        errorLabel.setFont(Theme.BODY_FONT_BOLD.deriveFont(12f));
        errorLabel.setForeground(Theme.WARN);
        gc.gridy++;
        gc.insets = new Insets(10, 0, 2, 0);
        card.add(errorLabel, gc);

        emailField.setPreferredSize(new Dimension(emailField.getPreferredSize().width, 36));
        passwordField.setPreferredSize(new Dimension(passwordField.getPreferredSize().width, 36));
        emailField.setFont(Theme.BODY_FONT);
        passwordField.setFont(Theme.BODY_FONT);

        gc.insets = new Insets(10, 0, 4, 0);
        gc.gridy++;
        JLabel emailLabel = new JLabel("Email");
        emailLabel.setFont(Theme.FIELD_LABEL_FONT);
        emailLabel.setForeground(Theme.MUTED.darker());
        card.add(emailLabel, gc);

        gc.gridy++;
        card.add(emailField, gc);

        gc.gridy++;
        gc.insets = new Insets(14, 0, 4, 0);
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(Theme.FIELD_LABEL_FONT);
        passLabel.setForeground(Theme.MUTED.darker());
        card.add(passLabel, gc);

        gc.gridy++;
        card.add(passwordField, gc);

        gc.gridy++;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.NONE;
        gc.insets = new Insets(26, 0, 12, 0);

        loginBtn.setPreferredSize(new Dimension(140, 40));
        loginBtn.setBackground(Theme.ACCENT);
        loginBtn.setForeground(Theme.WHITE);
        loginBtn.setFont(Theme.BODY_FONT_BOLD.deriveFont(15f));
        loginBtn.setFocusPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setBorderPainted(false);
        loginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(loginBtn, gc);

        gc.gridy++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        JButton toRegister = new JButton("No account? Register here");
        toRegister.setBorderPainted(false);
        toRegister.setContentAreaFilled(false);
        toRegister.setFont(Theme.BODY_FONT_BOLD);
        toRegister.setForeground(Theme.ACCENT);
        toRegister.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gc.insets = new Insets(4, 0, 0, 0);
        card.add(toRegister, gc);

        GridBagConstraints center = new GridBagConstraints();
        center.gridx = 0;
        center.gridy = 0;
        add(card, center);

        loginBtn.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());
        toRegister.addActionListener(e -> {
            errorLabel.setText(" ");
            app.showCard(AppWindow.CARD_REGISTER);
        });
    }

    private void doLogin() {
        errorLabel.setText(" ");
        loginBtn.setEnabled(false);
        loginBtn.setText("Processing\u2026");

        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (email.isBlank() || password.isBlank()) {
            loginBtn.setEnabled(true);
            loginBtn.setText("Login");
            errorLabel.setText("Enter both email and password.");
            return;
        }

        new SwingWorker<AuthService.LoginOutcome, Void>() {
            String error = null;

            @Override
            protected AuthService.LoginOutcome doInBackground() {
                try {
                    return ctx.auth.login(email, password);
                } catch (ApiException e) {
                    error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                loginBtn.setEnabled(true);
                loginBtn.setText("Login");

                AuthService.LoginOutcome outcome;
                try {
                    outcome = get();
                } catch (Exception e) {
                    outcome = null;
                }

                if (outcome == null) {
                    errorLabel.setText(error != null ? error : "Invalid credentials.");
                    return;
                }

                switch (outcome) {
                    case ONLINE, OFFLINE_OK -> {
                        passwordField.setText("");
                        boolean offline = outcome == AuthService.LoginOutcome.OFFLINE_OK;
                        app.onAuthenticated();
                        if (offline) {
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(app,
                                    "You're signed in from this device's offline cache. Some actions need a live connection and will queue until you're back online.",
                                    "Offline mode", JOptionPane.INFORMATION_MESSAGE));
                        }
                    }
                    case OFFLINE_NO_CACHED_ACCOUNT ->
                            errorLabel.setText("You're offline and this device has no saved sign-in for that email yet.");
                    case OFFLINE_BAD_PASSWORD ->
                            errorLabel.setText("That password doesn't match this device's saved sign-in.");
                }
            }
        }.execute();
    }
}
