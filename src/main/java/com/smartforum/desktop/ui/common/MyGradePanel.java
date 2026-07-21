package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Student-facing "My Grades" panel.
 * Designed to mirror the Laravel card-list layout for group grade summaries.
 */
public class MyGradePanel extends JPanel {

    private final AppContext ctx;
    private final JPanel cardsContainer = new JPanel();
    private final JScrollPane scrollPane;

    public MyGradePanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        // Header Title
        JLabel title = new JLabel("My Grades");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(title, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Container holding vertically stacked group grade cards
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setOpaque(false);

        // Scroll pane wrapper for cards
        scrollPane = new JScrollPane(cardsContainer);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Refreshes the panel by fetching membership groups and their grade details.
     */
    public void refresh() {
        cardsContainer.removeAll();
        cardsContainer.revalidate();
        cardsContainer.repaint();

        new SwingWorker<List<GroupGradeData>, Void>() {
            @Override
            protected List<GroupGradeData> doInBackground() {
                List<GroupGradeData> gradeList = new ArrayList<>();
                JSONArray groups;

                try {
                    JSONObject response = ctx.api.listGroups();
                    groups = response.optJSONArray("data", new JSONArray());
                } catch (ApiException | ApiOfflineException e) {
                    groups = new JSONArray();
                    ctx.store.cachedGroups().forEach(groups::put);
                }

                for (int i = 0; i < groups.length(); i++) {
                    JSONObject g = groups.getJSONObject(i);
                    if (g.optBoolean("is_member", false)) {
                        long groupId = g.optLong("group_id");
                        String groupName = g.optString("name", "Group");

                        JSONObject gradeObj = null;
                        try {
                            gradeObj = ctx.api.myGrade(groupId);
                        } catch (ApiException | ApiOfflineException ignored) {
                        }

                        gradeList.add(new GroupGradeData(groupId, groupName, gradeObj));
                    }
                }
                return gradeList;
            }

            @Override
            protected void done() {
                List<GroupGradeData> results;
                try {
                    results = get();
                } catch (Exception e) {
                    results = new ArrayList<>();
                }

                cardsContainer.removeAll();

                if (results.isEmpty()) {
                    JLabel emptyLabel = new JLabel("Join a group first to see your grades.");
                    emptyLabel.setFont(Theme.BODY_FONT);
                    emptyLabel.setForeground(Theme.INK);
                    cardsContainer.add(emptyLabel);
                } else {
                    for (GroupGradeData item : results) {
                        cardsContainer.add(createGradeCard(item));
                        cardsContainer.add(Box.createRigidArea(new Dimension(0, 14))); // Gap between cards
                    }
                }

                cardsContainer.revalidate();
                cardsContainer.repaint();
            }
        }.execute();
    }

    /**
     * Creates a card panel styled after the Laravel group grade summary component.
     */
    private JPanel createGradeCard(GroupGradeData data) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 6));
        card.setBackground(Theme.WHITE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Light gray rounded border matching web cards
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(16, 20, 16, 20)
        ));

        // Group Title
        JLabel groupNameLabel = new JLabel(data.groupName);
        groupNameLabel.setFont(Theme.BODY_FONT_BOLD);
        groupNameLabel.setForeground(new Color(30, 58, 138)); // Dark blue heading

        // Participation & Quizzes score row
        String participationScore = "0.00";
        String quizScore = "0.00";
        String overallTotal = "0.00";

        if (data.gradeData != null) {
            participationScore = String.valueOf(data.gradeData.opt("participation_total") != null ? data.gradeData.opt("participation_total") : "0.00");
            quizScore = String.valueOf(data.gradeData.opt("quiz_total") != null ? data.gradeData.opt("quiz_total") : "0.00");
            overallTotal = String.valueOf(data.gradeData.opt("overall_total") != null ? data.gradeData.opt("overall_total") : "0.00");
        }

        JLabel scoresLabel = new JLabel(String.format("Participation: %s  ·  Quizzes: %s", participationScore, quizScore));
        scoresLabel.setFont(Theme.BODY_FONT);
        scoresLabel.setForeground(new Color(100, 116, 139)); // Muted text

        // Overall Total row
        JLabel totalLabel = new JLabel(String.format("Overall total: %s", overallTotal));
        totalLabel.setFont(Theme.BODY_FONT_BOLD);
        totalLabel.setForeground(Theme.INK);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        groupNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        scoresLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        contentPanel.add(groupNameLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        contentPanel.add(scoresLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        contentPanel.add(totalLabel);

        card.add(contentPanel, BorderLayout.CENTER);

        return card;
    }

    private record GroupGradeData(long groupId, String groupName, JSONObject gradeData) {}
}