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
    private final Map<String, JLabel> navBadges = new LinkedHashMap<>();
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
                SwingUtilities.invokeLater(() -> statusBar.setOnline(online, ctx.store.pendingOutboxCount(), ctx.store.failedOutboxCount())));
        ctx.sync.setOnSyncComplete(result ->
                SwingUtilities.invokeLater(() -> statusBar.setOnline(result.success() || ctx.sync.isOnline(), ctx.store.pendingOutboxCount(), ctx.store.failedOutboxCount())));
        statusBar.setOnDismissFailed(() -> {
            ctx.store.clearFailedOutbox();
            statusBar.setOnline(ctx.sync.isOnline(), ctx.store.pendingOutboxCount(), ctx.store.failedOutboxCount());
        });
    }

    private JComponent buildTopBar(AppContext ctx) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PAPER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(16, 26, 16, 26)));

        JLabel brand = new JLabel("\uD83C\uDF93  SMART DISCUSSION FORUM");
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

        // FIX: every other row added to a BoxLayout in this sidebar (see navRow())
        // explicitly sets alignmentX + a maximumSize. statusBar never did, so
        // BoxLayout was free to squeeze its row down to almost nothing, leaving
        // only the dot glyph visible and hiding the "Online"/"Offline" text.
        statusBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

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
        icon.setForeground(iconColorFor(item.icon()));
        JLabel label = new JLabel(item.label());
        label.setFont(Theme.NAV_FONT);
        label.setForeground(Theme.PAPER);

        JLabel badge = new JLabel("", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xDC3545)); // matches web's Laravel Alert Red
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.setForeground(Color.WHITE);
        badge.setPreferredSize(new Dimension(26, 18));
        badge.setOpaque(false);
        badge.setVisible(false); // hidden until setBadgeCount() gives it a count > 0

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setOpaque(false);
        center.add(label, BorderLayout.CENTER);
        center.add(badge, BorderLayout.EAST);

        row.add(icon, BorderLayout.WEST);
        row.add(center, BorderLayout.CENTER);
        navLabels.put(item.key(), label);
        navBadges.put(item.key(), badge);
        return row;
    }

    /**
     * Quizzes, Recommended, and Notifications get their own accent color so
     * they stand out from the rest of the nav; every other icon keeps the
     * default light paper tone.
     */
    private static Color iconColorFor(String icon) {
        return switch (icon) {
            case "\uD83D\uDCDD" -> new Color(0xF0C36D); // \ud83d\udcdd Quizzes - gold
            case "\u2728" -> new Color(0x2DD4BF);       // \u2728 Recommended - teal
            case "\uD83D\uDD14" -> new Color(0xDC3545); // \ud83d\udd14 Notifications - red
            default -> Theme.PAPER;
        };
    }

    private JComponent buildUserFooter(AppContext ctx, Runnable onLogout) {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 40)),
                new EmptyBorder(12, 16, 14, 16)));

        AvatarView avatar = new AvatarView(38, new Color(0x2DD4BF), new Color(255, 255, 255, 60));
        avatar.setInitials(initialsFor(ctx.session.fullName()));
        String avatarUrl = ctx.session.user() != null ? ctx.session.user().optString("avatar_url", null) : null;
        avatar.loadFromUrl(avatarUrl);

        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(ctx.session.fullName());
        name.setForeground(Theme.PAPER);
        name.setFont(Theme.SIDEBAR_NAME_FONT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton logoutLink = footerLink("Log out", onLogout);
        logoutLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutLink.setMargin(new Insets(0, 0, 0, 0));

        textStack.add(name);
        textStack.add(Box.createVerticalStrut(2));
        textStack.add(logoutLink);

        footer.add(avatar, BorderLayout.WEST);
        footer.add(textStack, BorderLayout.CENTER);
        return footer;
    }

    /** First letter of the first and last name segments (e.g. "Carlos Bonaparte" -> "CB"), mirroring the Laravel client's avatar fallback. */
    private static String initialsFor(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = String.valueOf(parts[0].charAt(0));
        if (parts.length == 1) return first.toUpperCase();
        return (first + parts[parts.length - 1].charAt(0)).toUpperCase();
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
        navPanel.repaint();      // ADD THIS - repaints the whole sidebar list at once

        Runnable callback = onShowCallbacks.get(key);
        if (callback != null) {
            callback.run();
        }
    }

    /** Shows/updates a small red count badge on a sidebar nav item (e.g. unread notifications). Hides it when count <= 0. */
    public void setBadgeCount(String key, int count) {
        JLabel badge = navBadges.get(key);
        if (badge == null) return;
        if (count <= 0) {
            badge.setVisible(false);
        } else {
            badge.setText(count > 9 ? "9+" : String.valueOf(count));
            badge.setVisible(true);
        }
        badge.revalidate();
        badge.repaint();
    }

    public String activePanel() {
        return activeKey;
    }
}