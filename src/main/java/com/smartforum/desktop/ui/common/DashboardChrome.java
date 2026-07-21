package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared application shell, built as a JPanel so it can be dropped straight
 * into the root window's CardLayout alongside the login/register screens
 * (rather than opening as its own separate JFrame). Mirrors the web
 * client's layout:
 *   - a cream top bar with the brand on the left and a red "Welcome, X"
 *     message on the right (layouts/app.blade.php .app-topbar)
 *   - a full-height navy sidebar with role-based navigation and a
 *     user/profile footer (.app-sidebar)
 *   - a white content area that fills the rest of the window, switching
 *     between panels via CardLayout the same way the web client toggles
 *     .dash-panel visibility
 *
 * Nav row sizing/spacing and font scale are carried over from the previous
 * desktop client's DashboardChrome (58px rows, generous padding, larger
 * nav/footer fonts), which read as much better-spaced than tighter sizing -
 * only the colors were swapped over to the Laravel palette.
 */
public class DashboardChrome extends JPanel {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentArea = new JPanel(cardLayout);
    private final Map<String, JPanel> navRows = new LinkedHashMap<>();
    private final Map<String, JLabel> navLabels = new LinkedHashMap<>();
    private final StatusBar statusBar = new StatusBar();
    private final JLabel welcomeLabel = new JLabel();
    private String activeKey;
    private final Map<String, Runnable> onShowCallbacks = new LinkedHashMap<>();
    private JPanel navPanel;
    private static final Color ROW_HOVER = new Color(255, 255, 255, 18);

    public DashboardChrome(AppContext ctx, List<NavItem> navItems, Runnable onLogout) {
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);

        add(buildTopBar(ctx), BorderLayout.NORTH);
        add(buildBody(ctx, navItems, onLogout), BorderLayout.CENTER);

        ctx.sync.setOnConnectivityChange(online ->
                SwingUtilities.invokeLater(() -> statusBar.setOnline(online, ctx.store.pendingOutboxCount())));
        ctx.sync.setOnSyncComplete(result ->
                SwingUtilities.invokeLater(() -> statusBar.setOnline(result.success() || ctx.sync.isOnline(), ctx.store.pendingOutboxCount())));
    }

    private JComponent buildTopBar(AppContext ctx) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PAPER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(16, 26, 16, 26)));

        JLabel brand = new JLabel("\uD83D\uDE80  SMART DISCUSSION FORUM");
        brand.setFont(Theme.BRAND_FONT);
        brand.setForeground(Theme.INK);

        welcomeLabel.setFont(Theme.BODY_FONT_BOLD);
        welcomeLabel.setForeground(Theme.WARN);
        welcomeLabel.setText("Welcome, " + ctx.session.fullName());

        bar.add(brand, BorderLayout.WEST);
        bar.add(welcomeLabel, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildBody(AppContext ctx, List<NavItem> navItems, Runnable onLogout) {
        JPanel body = new JPanel(new BorderLayout());
        body.add(buildSidebar(ctx, navItems, onLogout), BorderLayout.WEST);

        contentArea.setBackground(Theme.WHITE);
        contentArea.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE));
        body.add(contentArea, BorderLayout.CENTER);
        return body;
    }

    private JComponent buildSidebar(AppContext ctx, List<NavItem> navItems, Runnable onLogout) {
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        navPanel = nav;   // ADD THIS
        nav.setOpaque(false);
        nav.setBorder(new EmptyBorder(14, 10, 4, 10));

        for (NavItem item : navItems) {
            JPanel row = navRow(item);
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showPanel(item.key());
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!item.key().equals(activeKey)) row.setBackground(ROW_HOVER);
                    navPanel.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!item.key().equals(activeKey)) row.setBackground(Theme.INK);
                    navPanel.repaint();
                }
            });
            navRows.put(item.key(), row);
            nav.add(row);
            nav.add(Box.createVerticalStrut(6));
        }

        JScrollPane navScroll = new JScrollPane(nav, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.setBorder(null);
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.INK);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.add(navScroll, BorderLayout.CENTER);

        JPanel footerWrap = new JPanel();
        footerWrap.setOpaque(false);
        footerWrap.setLayout(new BoxLayout(footerWrap, BoxLayout.Y_AXIS));
        footerWrap.add(statusBar);
        footerWrap.add(buildUserFooter(ctx, onLogout));
        sidebar.add(footerWrap, BorderLayout.SOUTH);

        return sidebar;
    }

    private JPanel navRow(NavItem item) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(true);
        row.setBackground(Theme.INK);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.INK),
                new EmptyBorder(13, 13, 13, 13)));

        JLabel icon = new JLabel(item.icon());
        icon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        JLabel label = new JLabel(item.label());
        label.setFont(Theme.NAV_FONT);
        label.setForeground(Theme.PAPER);

        row.add(icon, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        navLabels.put(item.key(), label);
        return row;
    }

    private JComponent buildUserFooter(AppContext ctx, Runnable onLogout) {
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 40)),
                new EmptyBorder(12, 16, 14, 16)));

        JLabel name = new JLabel(ctx.session.fullName());
        name.setForeground(new Color(0xF0C36D));
        name.setFont(Theme.SIDEBAR_NAME_FONT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel role = new JLabel(ctx.session.primaryRole());
        role.setForeground(Theme.PAPER);
        role.setFont(Theme.SMALL_FONT);
        role.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 4));
        links.setOpaque(false);
        links.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton profileLink = footerLink("My Profile", () -> showPanel("profile"));
        JButton logoutLink = footerLink("Log out", onLogout);
        links.add(profileLink);
        links.add(logoutLink);

        footer.add(name);
        footer.add(Box.createVerticalStrut(3));
        footer.add(role);
        footer.add(Box.createVerticalStrut(10));
        footer.add(links);
        return footer;
    }

    private JButton footerLink(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(Theme.BODY_FONT_BOLD);
        b.setForeground(new Color(0xBAE6FD));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(0, 0, 0, 0));
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Registers a panel under a key so a sidebar item (or code elsewhere) can switch to it. */
    public void addPanel(String key, JComponent panel) {
        contentArea.add(panel, key);
    }

    /** Registers a callback to run every time this panel key becomes visible (e.g. a panel's data refresh). */
    public void onNavigateTo(String key, Runnable callback) {
        onShowCallbacks.put(key, callback);
    }

    public void showPanel(String key) {
        cardLayout.show(contentArea, key);
        activeKey = key;
        navRows.forEach((k, row) -> {
            boolean active = k.equals(key);
            row.setBackground(active ? Theme.ACCENT : Theme.INK);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, active ? Theme.WARN : Theme.INK),
                    new EmptyBorder(13, 13, 13, 13)));
            JLabel label = navLabels.get(k);
            if (label != null) label.setFont(active ? Theme.NAV_FONT_ACTIVE : Theme.NAV_FONT);
        });

        navPanel.revalidate();   // ADD THIS
        navPanel.repaint();      // ADD THIS — repaints the whole sidebar list at once

        Runnable callback = onShowCallbacks.get(key);
        if (callback != null) {
            callback.run();
        }
    }

    public String activePanel() {
        return activeKey;
    }
}
