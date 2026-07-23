package com.smartforum.desktop.ui;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.ui.admin.AdminDashboard;
import com.smartforum.desktop.ui.common.DashboardChrome;
import com.smartforum.desktop.ui.common.Theme;
import com.smartforum.desktop.ui.lecturer.LecturerDashboard;
import com.smartforum.desktop.ui.student.StudentDashboard;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * The one and only application window.
 */
public class AppWindow extends JFrame {

    public static final String CARD_LOGIN = "login";
    public static final String CARD_REGISTER = "register";
    public static final String CARD_DASHBOARD = "dashboard";

    private final AppContext ctx;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JPanel dashboardHost = new JPanel(new BorderLayout());

    public AppWindow(AppContext ctx) {
        super("Smart Discussion Forum");
        this.ctx = ctx;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 780));
        setSize(1200, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.INK);

        // Apply high-resolution vector logo icon
        setIconImage(createGradCapAppIcon());

        cards.add(new LoginPanel(this, ctx), CARD_LOGIN);
        cards.add(new RegisterPanel(this, ctx), CARD_REGISTER);
        dashboardHost.setOpaque(false);
        cards.add(dashboardHost, CARD_DASHBOARD);

        setContentPane(cards);
    }

    /**
     * Renders a large, crisp vector logo badge matching the Laravel web design.
     */
    private static Image createGradCapAppIcon() {
        int size = 128; // High resolution for sharp taskbar/Alt+Tab display
        Image image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) image.getGraphics();

        // Enable maximum antialiasing quality
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 1. Dark Teal/Navy Rounded Square Container
        g2.setColor(new Color(20, 32, 38)); // Matches Web UI Sidebar/Header Dark Background
        g2.fillRoundRect(0, 0, size, size, 28, 28);

        // 2. Subtle Accent Border
        g2.setColor(new Color(38, 106, 88)); // Theme Primary Green
        g2.setStroke(new BasicStroke(3.0f));
        g2.drawRoundRect(1, 1, size - 3, size - 3, 28, 28);

        // 3. Vector Mortarboard (Cap Diamond)
        Path2D capTop = new Path2D.Double();
        capTop.moveTo(size * 0.50, size * 0.28); // Top vertex
        capTop.lineTo(size * 0.85, size * 0.42); // Right vertex
        capTop.lineTo(size * 0.50, size * 0.56); // Bottom vertex
        capTop.lineTo(size * 0.15, size * 0.42); // Left vertex
        capTop.closePath();

        g2.setColor(new Color(246, 245, 240)); // Warm White Cap Surface
        g2.fill(capTop);

        // Cap Shadow/Underside Depth
        g2.setColor(new Color(200, 205, 210));
        g2.setStroke(new BasicStroke(2.0f));
        g2.draw(capTop);

        // 4. Skullcap Base
        Path2D capBase = new Path2D.Double();
        capBase.moveTo(size * 0.32, size * 0.49);
        capBase.curveTo(size * 0.32, size * 0.68, size * 0.68, size * 0.68, size * 0.68, size * 0.49);
        capBase.lineTo(size * 0.68, size * 0.58);
        capBase.curveTo(size * 0.68, size * 0.73, size * 0.32, size * 0.73, size * 0.32, size * 0.58);
        capBase.closePath();

        g2.setColor(new Color(220, 225, 230));
        g2.fill(capBase);

        // 5. Gold Tassel Accent
        g2.setColor(new Color(234, 179, 8)); // Vibrant Gold
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Center Button
        g2.fillOval((int)(size * 0.48), (int)(size * 0.40), 6, 6);

        // Tassel String & Fringe
        Path2D tassel = new Path2D.Double();
        tassel.moveTo(size * 0.50, size * 0.42);
        tassel.lineTo(size * 0.22, size * 0.50);
        tassel.lineTo(size * 0.22, size * 0.68);
        g2.draw(tassel);

        // Tassel Fringe Ball
        g2.fillOval((int)(size * 0.19), (int)(size * 0.66), 8, 8);

        g2.dispose();
        return image;
    }

    public void showCard(String name) {
        cardLayout.show(cards, name);
    }

    /** Called after a successful login/registration: builds the right role's dashboard and shows it. */
    public void onAuthenticated() {
        ctx.sync.startPolling(20);

        dashboardHost.removeAll();
        DashboardChrome chrome = ctx.session.isAdministrator() ? AdminDashboard.build(ctx, this::logout)
                : ctx.session.isLecturer() ? LecturerDashboard.build(ctx, this::logout)
                : StudentDashboard.build(ctx, this::logout);
        dashboardHost.add(chrome, BorderLayout.CENTER);
        dashboardHost.revalidate();
        dashboardHost.repaint();

        showCard(CARD_DASHBOARD);
    }

    private void logout() {
        ctx.auth.logout();
        ctx.sync.stop();
        dashboardHost.removeAll();
        showCard(CARD_LOGIN);
    }
}