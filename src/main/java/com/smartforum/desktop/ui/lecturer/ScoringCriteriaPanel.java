package com.smartforum.desktop.ui.lecturer;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import com.smartforum.desktop.ui.common.Theme;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * Scoring Criteria panel formatted to match Laravel layout.
 * Tight wrap layout: shrinks to fit content and expands dynamically as rules are added.
 */
public class ScoringCriteriaPanel extends JPanel {

    private final AppContext ctx;
    private final JComboBox<GroupChoice> groupPicker = new JComboBox<>();
    private final JPanel criteriaList = new JPanel();
    private final JTextField descriptionField = new JTextField();
    private final JComboBox<ActivityType> activityTypeBox = new JComboBox<>(new ActivityType[]{
            new ActivityType("post", "Post"),
            new ActivityType("reply", "Reply"),
            new ActivityType("quiz_attempt", "Quiz attempt"),
            new ActivityType("topic_creation", "Topic creation"),
    });
    private final JTextField maxMarksField = new JTextField("10");
    private final JButton addBtn = new JButton("Add rule");
    private long selectedGroupId = -1;

    private record GroupChoice(long groupId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record ActivityType(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public ScoringCriteriaPanel(AppContext ctx) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 14));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        // Header Title
        JLabel title = new JLabel("Scoring Criteria");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.add(title, BorderLayout.WEST);
        add(titlePanel, BorderLayout.NORTH);

        // Main White Container Card with Green Accent Line
        JPanel cardContainer = new JPanel();
        cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.Y_AXIS));
        cardContainer.setBackground(Theme.WHITE);
        cardContainer.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new CompoundBorder(
                        new MatteBorder(0, 4, 0, 0, new Color(16, 185, 129)), // Green accent
                        new EmptyBorder(20, 24, 20, 24)
                )
        ));

        // Description Paragraph
        JLabel description = new JLabel("Define how much each activity is worth per group. "
                + "A group with no criteria for an activity type earns students zero participation points for it, even if they post.");
        description.setFont(Theme.BODY_FONT);
        description.setForeground(new Color(100, 116, 139));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Group Selector
        JLabel groupLabel = new JLabel("Group:");
        groupLabel.setFont(Theme.BODY_FONT);
        groupLabel.setForeground(Theme.INK);
        groupLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        groupPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        groupPicker.setPreferredSize(new Dimension(100, 36));
        groupPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        groupPicker.addActionListener(e -> {
            GroupChoice choice = (GroupChoice) groupPicker.getSelectedItem();
            if (choice != null) {
                selectedGroupId = choice.groupId();
                loadCriteria();
            }
        });

        // Criteria List Area
        criteriaList.setLayout(new BoxLayout(criteriaList, BoxLayout.Y_AXIS));
        criteriaList.setOpaque(false);
        criteriaList.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Bottom Form
        JPanel form = buildForm();
        form.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Assemble vertically stacked card elements
        cardContainer.add(description);
        cardContainer.add(Box.createRigidArea(new Dimension(0, 16)));
        cardContainer.add(groupLabel);
        cardContainer.add(Box.createRigidArea(new Dimension(0, 6)));
        cardContainer.add(groupPicker);
        cardContainer.add(Box.createRigidArea(new Dimension(0, 16)));
        cardContainer.add(criteriaList);
        cardContainer.add(Box.createRigidArea(new Dimension(0, 16)));
        cardContainer.add(form);

        // Wrapper to keep the card docked at the TOP so it doesn't stretch down
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(cardContainer, BorderLayout.NORTH);

        add(wrapper, BorderLayout.CENTER);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(241, 245, 249)),
                new EmptyBorder(16, 0, 0, 0)
        ));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 4, 0, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridy = 0;

        descriptionField.setPreferredSize(new Dimension(100, 36));
        descriptionField.setToolTipText("e.g. Discussion post");

        activityTypeBox.setPreferredSize(new Dimension(100, 36));
        maxMarksField.setPreferredSize(new Dimension(75, 36));

        addBtn.setPreferredSize(new Dimension(95, 36));
        addBtn.setBackground(new Color(27, 77, 62)); // #1B4D3E
        addBtn.setForeground(Color.WHITE);
        addBtn.setFont(Theme.BODY_FONT_BOLD);
        addBtn.setFocusPainted(false);
        addBtn.setOpaque(true);
        addBtn.setBorderPainted(false);
        addBtn.addActionListener(e -> addRule());

        gc.gridx = 0; gc.weightx = 3.0;
        form.add(labeledColumn("Description", descriptionField), gc);

        gc.gridx = 1; gc.weightx = 1.5;
        form.add(labeledColumn("Activity type", activityTypeBox), gc);

        gc.gridx = 2; gc.weightx = 1.0;
        form.add(labeledColumn("Max marks", maxMarksField), gc);

        gc.gridx = 3; gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.SOUTH;
        form.add(addBtn, gc);

        return form;
    }

    private JPanel labeledColumn(String label, JComponent field) {
        JPanel col = new JPanel(new BorderLayout(0, 4));
        col.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        l.setForeground(Theme.INK);
        col.add(l, BorderLayout.NORTH);
        col.add(field, BorderLayout.CENTER);
        return col;
    }

    public void refresh() {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    JSONObject response = ctx.api.listGroups();
                    return response.optJSONArray("data", new JSONArray());
                } catch (ApiException | ApiOfflineException e) {
                    JSONArray arr = new JSONArray();
                    ctx.store.cachedGroups().forEach(arr::put);
                    return arr;
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
                GroupChoice previous = (GroupChoice) groupPicker.getSelectedItem();
                groupPicker.removeAllItems();
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject g = groups.getJSONObject(i);
                    if (g.optBoolean("is_owner", false) || g.optBoolean("is_group_admin", false)) {
                        groupPicker.addItem(new GroupChoice(g.getLong("group_id"), g.optString("name", "Group")));
                    }
                }
                if (groupPicker.getItemCount() == 0) {
                    criteriaList.removeAll();
                    JLabel noGroupLabel = new JLabel("Select a group above.");
                    noGroupLabel.setFont(Theme.BODY_FONT);
                    noGroupLabel.setForeground(new Color(100, 116, 139));
                    criteriaList.add(noGroupLabel);
                    criteriaList.revalidate();
                    criteriaList.repaint();
                } else if (previous != null) {
                    groupPicker.setSelectedItem(previous);
                } else {
                    groupPicker.setSelectedIndex(0);
                }
            }
        }.execute();
    }

    private void loadCriteria() {
        long groupId = selectedGroupId;
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    return ctx.api.scoringCriteria(groupId);
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONArray criteria;
                try {
                    criteria = get();
                } catch (Exception e) {
                    criteria = null;
                }
                criteriaList.removeAll();
                if (criteria == null) {
                    JLabel errLabel = new JLabel("Scoring criteria need an internet connection to load.");
                    errLabel.setForeground(new Color(100, 116, 139));
                    criteriaList.add(errLabel);
                } else if (criteria.isEmpty()) {
                    JLabel emptyLabel = new JLabel("No scoring rules yet for this group.");
                    emptyLabel.setFont(Theme.BODY_FONT);
                    emptyLabel.setForeground(new Color(100, 116, 139));
                    criteriaList.add(emptyLabel);
                } else {
                    // Header Row
                    criteriaList.add(createHeaderRow());
                    for (int i = 0; i < criteria.length(); i++) {
                        criteriaList.add(criteriaRow(criteria.getJSONObject(i)));
                    }
                }
                criteriaList.revalidate();
                criteriaList.repaint();
            }
        }.execute();
    }

    private JComponent createHeaderRow() {
        JPanel header = new JPanel(new GridLayout(1, 3, 10, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 4, 8, 4));

        JLabel descHeader = new JLabel("Description");
        descHeader.setFont(Theme.BODY_FONT_BOLD);
        descHeader.setForeground(Theme.INK);

        JLabel actHeader = new JLabel("Activity");
        actHeader.setFont(Theme.BODY_FONT_BOLD);
        actHeader.setForeground(Theme.INK);

        JLabel maxHeader = new JLabel("Max marks");
        maxHeader.setFont(Theme.BODY_FONT_BOLD);
        maxHeader.setForeground(Theme.INK);

        header.add(descHeader);
        header.add(actHeader);
        header.add(maxHeader);
        return header;
    }

    private JComponent criteriaRow(JSONObject c) {
        JPanel row = new JPanel(new GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        row.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(241, 245, 249)),
                new EmptyBorder(10, 4, 10, 4)
        ));

        JLabel desc = new JLabel(c.optString("description", ""));
        desc.setFont(Theme.BODY_FONT);
        desc.setForeground(new Color(100, 116, 139));

        JLabel activity = new JLabel(c.optString("activity_type", ""));
        activity.setFont(Theme.BODY_FONT);
        activity.setForeground(new Color(100, 116, 139));

        Object marksObj = c.opt("max_marks");
        String marksStr = marksObj != null ? String.format("%.2f", Double.parseDouble(marksObj.toString())) : "0.00";
        JLabel marks = new JLabel(marksStr);
        marks.setFont(Theme.BODY_FONT);
        marks.setForeground(new Color(100, 116, 139));

        row.add(desc);
        row.add(activity);
        row.add(marks);

        return row;
    }

    private void addRule() {
        if (selectedGroupId < 0) {
            JOptionPane.showMessageDialog(this, "Select a group first.");
            return;
        }
        String description = descriptionField.getText().trim();
        if (description.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a description for this rule.");
            return;
        }
        ActivityType activityType = (ActivityType) activityTypeBox.getSelectedItem();
        double maxMarks;
        try {
            maxMarks = Double.parseDouble(maxMarksField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Max marks must be a number.");
            return;
        }

        long groupId = selectedGroupId;
        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.addScoringCriteria(groupId, description, activityType.value(), maxMarks);
                } catch (ApiOfflineException e) {
                    error = "Adding a scoring rule needs an internet connection.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(ScoringCriteriaPanel.this, error, "Couldn't add rule", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                descriptionField.setText("");
                maxMarksField.setText("10");
                loadCriteria();
            }
        }.execute();
    }
}