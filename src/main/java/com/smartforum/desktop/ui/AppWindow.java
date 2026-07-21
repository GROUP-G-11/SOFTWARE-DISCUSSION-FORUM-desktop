package com.smartforum.desktop.ui;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.ui.admin.AdminDashboard;
import com.smartforum.desktop.ui.common.DashboardChrome;
import com.smartforum.desktop.ui.common.Theme;
import com.smartforum.desktop.ui.lecturer.LecturerDashboard;
import com.smartforum.desktop.ui.student.StudentDashboard;

import javax.swing.*;
import java.awt.*;

/**
 * The one and only application window. Everything - sign in, registration,
 * and every role's dashboard - is a card inside a single CardLayout here,
 * the same "single page application" shape the previous desktop client's
 * Main.java used, rather than separate JFrames popping open and closing.
 * Clicking a link or button switches cards; the window itself never closes
 * and reopens.
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

        cards.add(new LoginPanel(this, ctx), CARD_LOGIN);
        cards.add(new RegisterPanel(this, ctx), CARD_REGISTER);
        dashboardHost.setOpaque(false);
        cards.add(dashboardHost, CARD_DASHBOARD);

        setContentPane(cards);
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
