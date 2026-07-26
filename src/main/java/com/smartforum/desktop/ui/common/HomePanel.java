package com.smartforum.desktop.ui.common;
 
import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;
 
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
 
/**
 * Post-login landing panel, shown before Groups - mirrors the web client's
 * Home panel (gradient welcome banner, a stat card, a "top 3 groups"
 * leaderboard, and quick links to jump elsewhere).
 */
public class HomePanel extends JPanel {
 
   public record QuickLink(String icon, String title, String subtitle, String targetPanelKey, Runnable customAction) {
        public QuickLink(String icon, String title, String subtitle, String targetPanelKey) {
            this(icon, title, subtitle, targetPanelKey, null);
        }
    }

    private final AppContext ctx;
    private final boolean forLecturer;
    private final Consumer<String> navigate;

    private JLabel topicsValueLabel;

    private List<Long> managedGroupIds = new ArrayList<>();
    private List<String> managedGroupNames = new ArrayList<>();

    private JLabel quizzesValueLabel;
 
    private final JLabel greeting = new JLabel("Welcome back!");
    private final JLabel statValue = new JLabel("\u2013");
    private final JLabel statLabel = new JLabel();
    private final JPanel leaderboardPanel = new JPanel();
    private final JPanel quickLinksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
 
  private static final Color GREEN = Color.decode("#22C55E");
    private static final Color ORANGE = Color.decode("#F97316");
    private static final Color INDIGO = Color.decode("#6366F1");

    public HomePanel(AppContext ctx, boolean forLecturer, Consumer<String> navigate, List<QuickLink> quickLinks) {
        this.ctx = ctx;
        this.forLecturer = forLecturer;
        this.navigate = navigate;
 
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(28, 32, 28, 32));
        setBackground(Theme.WHITE);
 
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
 
        content.add(banner());
        content.add(Box.createVerticalStrut(16));
        content.add(statsRow());
        content.add(Box.createVerticalStrut(20));
 
        JLabel quickLinksTitle = new JLabel("Quick links");
        quickLinksTitle.setFont(Theme.HEADING_FONT_SM);
        quickLinksTitle.setForeground(Theme.INK);
        quickLinksTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(quickLinksTitle);
        content.add(Box.createVerticalStrut(10));
 
        quickLinksRow.setOpaque(false);
        quickLinksRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (QuickLink link : quickLinks) {
            quickLinksRow.add(quickLinkCard(link));
        }

        if (forLecturer) {
    quickLinksRow.add(
        quickLinkCard(
            new QuickLink(
                "📨",
                "Join Requests",
                "Approve or reject students requesting to join your groups.",
                null,
                this::openAllJoinRequests
            )
        )
    );
}
        content.add(quickLinksRow);
 
        add(new JScrollPane(content), BorderLayout.CENTER);
    }
 
    private JComponent banner() {
        JPanel banner = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, Theme.ACCENT, getWidth(), getHeight(), new Color(0xEC4899));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS, Theme.RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(26, 26, 26, 26));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
 
        greeting.setFont(Theme.HEADING_FONT.deriveFont(24f));
        greeting.setForeground(Color.WHITE);
 
        JLabel sub = new JLabel(forLecturer
                ? "Here's a snapshot of your groups and quizzes."
                : "Here's what's happening across your groups today.");
        sub.setFont(Theme.BODY_FONT);
        sub.setForeground(new Color(255, 255, 255, 220));
 
        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.add(greeting);
        textStack.add(Box.createVerticalStrut(4));
        textStack.add(sub);
 
        banner.add(textStack, BorderLayout.WEST);
        return banner;
    }

    private void openAllJoinRequests() {
        new AllJoinRequestsDialog(SwingUtilities.getWindowAncestor(this), ctx, managedGroupIds, managedGroupNames).setVisible(true);
    }
 
   private JComponent statsRow() {
        int cardCount = forLecturer ? 3 : 2;
        JPanel row = new JPanel(new GridLayout(1, cardCount, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(cardCount * 250, 110));
        row.setPreferredSize(new Dimension(cardCount * 250, 110));

        row.add(compactStatCard(statValue, forLecturer ? "Groups you manage" : "Groups joined", GREEN, "\uD83D\uDC65"));

        JLabel topicsValue = new JLabel("\u2013");
        this.topicsValueLabel = topicsValue;
        row.add(compactStatCard(topicsValue, "My Topics", ORANGE, "\uD83D\uDCAC"));

        if (forLecturer) {
            JLabel quizzesValue = new JLabel("\u2013");
            this.quizzesValueLabel = quizzesValue;
            row.add(compactStatCard(quizzesValue, "Your Quizzes", INDIGO, "\uD83D\uDCDD"));
        }

        return row;
    }

    private JComponent compactStatCard(JLabel valueLabel, String labelText, Color accent, String icon) {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(14, 0));
        card.setBackground(Theme.WHITE);

        JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 22));
        iconLabel.setPreferredSize(new Dimension(52, 52));
        iconLabel.setOpaque(false);

        valueLabel.setFont(Theme.HEADING_FONT.deriveFont(28f));
        valueLabel.setForeground(accent);

        JLabel sub = new JLabel(labelText);
        sub.setFont(Theme.SMALL_FONT);
        sub.setForeground(Theme.MUTED);

        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.add(valueLabel);
        textStack.add(sub);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(textStack, BorderLayout.CENTER);
        return card;
    }

    private JComponent quickLinkCard(QuickLink link) {
        CardPanel card = new CardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.WHITE);
        card.setPreferredSize(new Dimension(190, 92));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
 
        JLabel title = new JLabel(link.icon() + "  " + link.title());
        title.setFont(Theme.BODY_FONT_BOLD.deriveFont(14f));
        title.setForeground(Theme.INK);
 
        JLabel sub = new JLabel("<html><body style='width:150px'>" + link.subtitle() + "</body></html>");
        sub.setFont(Theme.SMALL_FONT);
        sub.setForeground(Theme.MUTED);
 
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(sub);
 
       card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (link.customAction() != null) {
                    link.customAction().run();
                } else {
                    navigate.accept(link.targetPanelKey());
                }
            }
        });
 
        return card;
    }
 
    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------
 
    public void refresh() {
        if (ctx.session != null && ctx.session.fullName() != null) {
            String firstName = ctx.session.fullName().split(" ")[0];
            greeting.setText("Welcome back, " + firstName + "!");
        }
 
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listGroups();
                    JSONArray data = response.optJSONArray("data", null);
                    return data != null ? data : new JSONArray();
                } catch (ApiException | ApiOfflineException e) {
                    return new JSONArray();
                }
            }
 
            @Override
            protected void done() {
                JSONArray groups;
                try {
                    groups = get();
                } catch (Exception e) {
                    groups = new JSONArray();
                }
                onGroupsLoaded(groups);
            }
        }.execute();
    }
 
   private void onGroupsLoaded(JSONArray groups) {
        int groupCount = 0;
        int topicCount = 0;
        List<Long> managedGroupIds = new ArrayList<>();
        List<String> managedGroupNames = new ArrayList<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.getJSONObject(i);
            if (g.optBoolean("is_banned", false)) continue;
            boolean eligible = forLecturer
                    ? (g.optBoolean("is_owner", false) || g.optBoolean("is_group_admin", false))
                    : (g.optBoolean("is_member", false) || g.optBoolean("is_group_admin", false));
            if (eligible) {
                groupCount++;
                topicCount += g.optInt("topics_count", 0);
                if (forLecturer) {
                    managedGroupIds.add(g.optLong("group_id", -1));
                    managedGroupNames.add(g.optString("name", "Group"));
                }
            }
        }
        statValue.setText(String.valueOf(groupCount));
        if (topicsValueLabel != null) topicsValueLabel.setText(String.valueOf(topicCount));

        this.managedGroupIds = managedGroupIds;
        this.managedGroupNames = managedGroupNames;

        if (forLecturer && quizzesValueLabel != null) {
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() {
                    try {
                        return ctx.api.myQuizzes().length();
                    } catch (ApiException | ApiOfflineException e) {
                        return 0;
                    }
                }

                @Override
                protected void done() {
                    try {
                        quizzesValueLabel.setText(String.valueOf(get()));
                    } catch (Exception ignored) {
                    }
                }
            }.execute();
        }
    }
}