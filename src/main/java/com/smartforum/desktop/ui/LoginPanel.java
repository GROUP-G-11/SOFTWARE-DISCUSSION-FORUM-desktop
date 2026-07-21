package com.smartforum.desktop.ui;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.auth.AuthService;
import com.smartforum.desktop.ui.common.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoginPanel extends JPanel {

    private final AppWindow app;
    private final AppContext ctx;
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton loginBtn = new JButton("Log in");

    // Colors matching Laravel UI
    private static final Color DARK_SIDE_BG = new Color(20, 32, 38);
    private static final Color LIGHT_SIDE_BG = new Color(246, 245, 240);
    private static final Color INPUT_BG = new Color(235, 242, 255);
    private static final Color INPUT_BORDER = new Color(210, 222, 245);
    private static final Color PRIMARY_GREEN = new Color(38, 106, 88);
    private static final Color TEXT_MUTED = new Color(140, 150, 160);

    public LoginPanel(AppWindow app, AppContext ctx) {
        this.app = app;
        this.ctx = ctx;

        // Split-screen Layout: Left Hero Banner | Right Login Form
        setLayout(new GridLayout(1, 2));

        // -----------------------------------------------------------------
        // LEFT SIDE: Branding & Hero Panel
        // -----------------------------------------------------------------
        JPanel leftPanel = new JPanel();
        leftPanel.setBackground(DARK_SIDE_BG);
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setBorder(new EmptyBorder(40, 48, 40, 48));

        // Top Brand Header
        JLabel brandLabel = new JLabel("🎓 SMART DISCUSSION FORUM");
        brandLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        brandLabel.setForeground(Color.WHITE);

        // Center Hero Text Container
        JPanel centerHero = new JPanel();
        centerHero.setOpaque(false);
        centerHero.setLayout(new BoxLayout(centerHero, BoxLayout.Y_AXIS));

        // Shield / Security Badge Icon
        JLabel shieldIcon = new JLabel("🛡") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 15));
                g2.drawOval(0, 0, 44, 44);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        shieldIcon.setFont(new Font("SansSerif", Font.PLAIN, 20));
        shieldIcon.setForeground(new Color(212, 175, 55));
        shieldIcon.setHorizontalAlignment(SwingConstants.CENTER);
        shieldIcon.setPreferredSize(new Dimension(46, 46));
        shieldIcon.setMaximumSize(new Dimension(46, 46));
        shieldIcon.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heroTitle = new JLabel("<html>Where the discussion<br>continues.</html>");
        heroTitle.setFont(new Font("Serif", Font.PLAIN, 32));
        heroTitle.setForeground(Color.WHITE);
        heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heroSubtitle = new JLabel("<html>Log in to reach your groups, follow topics, and keep up<br>with your quizzes and grades — all in one place.</html>");
        heroSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        heroSubtitle.setForeground(TEXT_MUTED);
        heroSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        centerHero.add(Box.createVerticalGlue());
        centerHero.add(shieldIcon);
        centerHero.add(Box.createVerticalStrut(24));
        centerHero.add(heroTitle);
        centerHero.add(Box.createVerticalStrut(16));
        centerHero.add(heroSubtitle);
        centerHero.add(Box.createVerticalGlue());

        // Bottom Footer Tagline
        JLabel footerLabel = new JLabel("<html>A discussion & learning space for students, lecturers, and<br>administrators.</html>");
        footerLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        footerLabel.setForeground(TEXT_MUTED.darker());

        leftPanel.add(brandLabel, BorderLayout.NORTH);
        leftPanel.add(centerHero, BorderLayout.CENTER);
        leftPanel.add(footerLabel, BorderLayout.SOUTH);

        // -----------------------------------------------------------------
        // RIGHT SIDE: Login Form Panel
        // -----------------------------------------------------------------
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBackground(LIGHT_SIDE_BG);

        JPanel formCard = new JPanel();
        formCard.setOpaque(false);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setPreferredSize(new Dimension(380, 480));

        // Welcome Header
        JLabel title = new JLabel("Welcome Back");
        title.setFont(new Font("Serif", Font.PLAIN, 38));
        title.setForeground(new Color(24, 30, 38));

        JLabel subtitle = new JLabel("Log in to your discussion forum account.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(new Color(110, 118, 128));

        errorLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        errorLabel.setForeground(new Color(200, 50, 50));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Email Section
        JLabel emailLabel = new JLabel("Email");
        emailLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        emailLabel.setForeground(new Color(60, 70, 80));

        JPanel emailContainer = createInputWrapper(emailField, "✉", null);

        // Password Section
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        passLabel.setForeground(new Color(60, 70, 80));

        // Password Eye Toggle Icon
        JLabel eyeIcon = new JLabel("👁");
        eyeIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        eyeIcon.setFont(new Font("SansSerif", Font.PLAIN, 14));
        eyeIcon.setForeground(TEXT_MUTED);
        eyeIcon.addMouseListener(new MouseAdapter() {
            private boolean showPass = false;

            @Override
            public void mouseClicked(MouseEvent e) {
                showPass = !showPass;
                passwordField.setEchoChar(showPass ? (char) 0 : '•');
            }
        });

        JPanel passwordContainer = createInputWrapper(passwordField, "🔒", eyeIcon);

        // Login Button
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginBtn.setBackground(PRIMARY_GREEN);
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginBtn.setFocusPainted(false);
        loginBtn.setBorderPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Footer Link
        JPanel registerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        registerRow.setOpaque(false);
        JLabel noAccLabel = new JLabel("No account?");
        noAccLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        noAccLabel.setForeground(new Color(100, 110, 120));

        JButton toRegister = new JButton("Register here");
        toRegister.setBorderPainted(false);
        toRegister.setContentAreaFilled(false);
        toRegister.setFont(new Font("SansSerif", Font.BOLD, 13));
        toRegister.setForeground(PRIMARY_GREEN);
        toRegister.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        registerRow.add(noAccLabel);
        registerRow.add(toRegister);
        registerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Assemble Form
        formCard.add(title);
        formCard.add(Box.createVerticalStrut(6));
        formCard.add(subtitle);
        formCard.add(Box.createVerticalStrut(10));
        formCard.add(errorLabel);
        formCard.add(Box.createVerticalStrut(12));
        formCard.add(emailLabel);
        formCard.add(Box.createVerticalStrut(6));
        formCard.add(emailContainer);
        formCard.add(Box.createVerticalStrut(16));
        formCard.add(passLabel);
        formCard.add(Box.createVerticalStrut(6));
        formCard.add(passwordContainer);
        formCard.add(Box.createVerticalStrut(24));
        formCard.add(loginBtn);
        formCard.add(Box.createVerticalStrut(16));
        formCard.add(registerRow);

        rightPanel.add(formCard);

        // Add both left and right sides
        add(leftPanel);
        add(rightPanel);

        // Action Listeners
        loginBtn.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());
        toRegister.addActionListener(e -> {
            errorLabel.setText(" ");
            app.showCard(AppWindow.CARD_REGISTER);
        });
    }

    private JPanel createInputWrapper(JTextField field, String prefixIcon, JComponent suffixComp) {
        JPanel wrapper = new JPanel(new BorderLayout(8, 0));
        wrapper.setBackground(INPUT_BG);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel prefix = new JLabel(prefixIcon);
        prefix.setFont(new Font("SansSerif", Font.PLAIN, 14));
        prefix.setForeground(new Color(150, 160, 175));

        field.setBorder(null);
        field.setOpaque(false);
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));

        wrapper.add(prefix, BorderLayout.WEST);
        wrapper.add(field, BorderLayout.CENTER);
        if (suffixComp != null) {
            wrapper.add(suffixComp, BorderLayout.EAST);
        }

        return wrapper;
    }

    private void doLogin() {
        errorLabel.setText(" ");
        loginBtn.setEnabled(false);
        loginBtn.setText("Processing…");

        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (email.isBlank() || password.isBlank()) {
            loginBtn.setEnabled(true);
            loginBtn.setText("Log in");
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
                loginBtn.setText("Log in");

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