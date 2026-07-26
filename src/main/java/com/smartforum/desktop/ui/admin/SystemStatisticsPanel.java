package com.smartforum.desktop.ui.admin;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import com.smartforum.desktop.ui.common.Theme;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;


public class SystemStatisticsPanel extends JPanel {

    private final AppContext ctx;
    private final JPanel cardsContainer = new WrapPanel(FlowLayout.LEFT, 12, 12);
    private final JPanel rolesPanel = new WrapPanel(FlowLayout.LEFT, 10, 8);

    public SystemStatisticsPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(20, 24, 20, 24));
        setBackground(Theme.WHITE);

        // Header + Refresh Button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("System Overview");
        title.setFont(Theme.HEADING_FONT.deriveFont(24f));
        title.setForeground(Theme.INK);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setPreferredSize(new Dimension(85, 32));
        refreshBtn.setBackground(Color.WHITE);
        refreshBtn.setForeground(new Color(27, 77, 62));
        refreshBtn.setFont(Theme.BODY_FONT_BOLD);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(new LineBorder(new Color(203, 213, 225), 1, true));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> refresh());

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        cardsContainer.setOpaque(false);

        // "Users by role" Box
        JPanel roleCard = new JPanel(new BorderLayout(0, 8));
        roleCard.setBackground(Theme.WHITE);
        roleCard.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JLabel roleTitle = new JLabel("Users by role");
        roleTitle.setFont(Theme.BODY_FONT_BOLD);
        roleTitle.setForeground(Theme.INK);

        rolesPanel.setOpaque(false);

        roleCard.add(roleTitle, BorderLayout.NORTH);
        roleCard.add(rolesPanel, BorderLayout.CENTER);

        // Vertical Content Wrapper
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.add(headerPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        contentPanel.add(cardsContainer);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        contentPanel.add(roleCard);

        add(contentPanel, BorderLayout.NORTH);
    }

    public void refresh() {
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.systemStatistics();
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONObject stats;
                try {
                    stats = get();
                } catch (Exception e) {
                    stats = null;
                }
                render(stats);
            }
        }.execute();
    }

    private void render(JSONObject stats) {
        cardsContainer.removeAll();
        rolesPanel.removeAll();

        if (stats == null) {
            cardsContainer.add(new JLabel("Statistics need an internet connection to load."));
        } else {
            cardsContainer.add(statCard("TOTAL USERS", stats.optInt("total_users", 0)));
            cardsContainer.add(statCard("TOTAL GROUPS", stats.optInt("total_groups", 0)));
            cardsContainer.add(statCard("TOTAL TOPICS", stats.optInt("total_topics", 0)));
            cardsContainer.add(statCard("TOTAL POSTS", stats.optInt("total_posts", 0)));
            cardsContainer.add(statCard("TOTAL REPLIES", stats.optInt("total_replies", 0)));
            cardsContainer.add(statCard("ACTIVE (7 DAYS)", stats.optInt("active_users_last_7_days", 0)));
            cardsContainer.add(statCard("CURRENTLY BLACKLISTED", stats.optInt("currently_blacklisted_users", 0)));

            JSONObject byRole = stats.optJSONObject("users_by_role");
            if (byRole != null) {
                for (String role : byRole.keySet()) {
                    int count = byRole.optInt(role, 0);
                    rolesPanel.add(createRolePill(role.toUpperCase(), count));
                }
            }
        }
        cardsContainer.revalidate();
        cardsContainer.repaint();
        rolesPanel.revalidate();
        rolesPanel.repaint();
    }

    private JComponent statCard(String label, int value) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.WHITE);
        card.setPreferredSize(new Dimension(170, 80));
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel valueLbl = new JLabel(String.valueOf(value));
        valueLbl.setFont(Theme.HEADING_FONT.deriveFont(26f));
        valueLbl.setForeground(Theme.INK);
        valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel labelLbl = new JLabel(label);
        labelLbl.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD, 10f));
        labelLbl.setForeground(new Color(100, 116, 139));
        labelLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(valueLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(labelLbl);

        return card;
    }

    private JComponent createRolePill(String roleName, int count) {
        Color bg;
        Color fg;

        switch (roleName) {
            case "ADMINISTRATOR":
            case "ADMIN":
                bg = new Color(254, 226, 226);
                fg = new Color(185, 28, 28);
                break;
            case "LECTURER":
                bg = new Color(224, 242, 254);
                fg = new Color(3, 105, 161);
                break;
            default:
                bg = new Color(241, 245, 249);
                fg = new Color(51, 65, 85);
                break;
        }

        JLabel pill = new JLabel(roleName + ": " + count) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };

        pill.setOpaque(false);
        pill.setBackground(bg);
        pill.setForeground(fg);
        pill.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD, 11f));
        pill.setBorder(new EmptyBorder(5, 12, 5, 12));

        return pill;
    }

    /**
     * FlowLayout subclass that correctly recalculates height on wrapping lines.
     */
    private static class WrapPanel extends JPanel {
        public WrapPanel(int align, int hgap, int vgap) {
            super(new FlowLayout(align, hgap, vgap));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension dim = super.getPreferredSize();
            if (getGrandparentWidth() > 0) {
                dim.width = getGrandparentWidth();
            }
            return dim;
        }

        private int getGrandparentWidth() {
            Container parent = getParent();
            return (parent != null) ? parent.getWidth() : 0;
        }
    }
}
