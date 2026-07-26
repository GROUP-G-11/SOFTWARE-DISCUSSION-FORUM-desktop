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

/**
 * Quiz Engine Module (SDD 5.5). Two modes:
 *   - Student (canManage=false): a simple list of quizzes across all their
 *     groups (GET /me/quizzes), with "Begin Quiz" on anything Open.
 *   - Lecturer (canManage=true): an inline "Create a new quiz" form stacked
 *     above "Your quizzes".
 *
 * Visual pass: every field/card here is custom-painted rounded to match
 * the web client's look (Theme.RADIUS), instead of bare square Swing
 * components. Outer padding/scroll structure mirrors NotificationsPanel and
 * RecommendedTopicsPanel exactly (EmptyBorder(28,36,28,36), single
 * JScrollPane over a BoxLayout body) so scrolling feels the same everywhere.
 * Button colors: all actions are green (primary) except "Remove", which is
 * red (danger) since it's destructive.
 */
public class QuizListPanel extends JPanel {

    private final AppContext ctx;
    private final boolean canManage;
    private final JPanel quizListBody = new JPanel();

    // ---- lecturer-only: create-quiz form state ----
    private final JComboBox<GroupChoice> targetGroupBox = new JComboBox<>();
    private final JTextField titleField = new JTextField();
    private final JTextField dateField = new JTextField();
    private final JTextField timeField = new JTextField();
    private final JTextField durationField = new JTextField("30");
    private final JPanel questionMatrix = new JPanel();
    private final List<QuestionRow> questionRows = new ArrayList<>();

    private record GroupChoice(long groupId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public QuizListPanel(AppContext ctx, boolean canManage) {
        this.ctx = ctx;
        this.canManage = canManage;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(28, 36, 28, 36));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("Quizzes");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);

        // Same structure as NotificationsPanel/RecommendedTopicsPanel: title
        // fixed in NORTH, everything scrollable lives in ONE JScrollPane in
        // CENTER, for the same smooth mouse-wheel / touchpad scrolling.
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        if (canManage) {
            JComponent createForm = buildCreateForm();
            createForm.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(createForm);
            body.add(Box.createVerticalStrut(20));

            JLabel listTitle = new JLabel("Your quizzes");
            listTitle.setFont(Theme.HEADING_FONT_SM);
            listTitle.setForeground(Theme.INK);
            listTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
            listTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(listTitle);
        }

        quizListBody.setLayout(new BoxLayout(quizListBody, BoxLayout.Y_AXIS));
        quizListBody.setOpaque(false);
        quizListBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(quizListBody);

        JScrollPane scrollPane = new JScrollPane(body);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------
    // Shared visual helpers - rounded card + rounded field styling
    // ------------------------------------------------------------------

    /** A custom-painted rounded rect container, optionally with a colored left accent stripe. */
    private static JPanel roundedPanel(Color bg, Color stripe) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g2.setColor(Theme.LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                if (stripe != null) {
                    g2.setColor(stripe);
                    g2.fillRoundRect(0, 0, 6, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                }
                g2.dispose();
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        return p;
    }

    private static void styleField(JTextField f) {
        f.setFont(Theme.BODY_FONT);
        f.setBackground(Theme.WHITE);
        f.setForeground(Theme.INK);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
    }

    private static void styleCombo(JComboBox<?> c) {
        c.setFont(Theme.BODY_FONT);
        c.setBackground(Theme.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
    }

    // ------------------------------------------------------------------
    // Lecturer: inline "Create a new quiz" form
    // ------------------------------------------------------------------

    private JComponent buildCreateForm() {
        JPanel card = roundedPanel(Theme.PAPER_DIM, Theme.SKY);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(new EmptyBorder(20, 28, 20, 20));

        JLabel formTitle = new JLabel("Create a new quiz");
        formTitle.setFont(Theme.HEADING_FONT_SM);
        formTitle.setForeground(Theme.INK);

        JPanel metaGrid = new JPanel(new GridBagLayout());
        metaGrid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 6, 4, 6);
        gc.gridy = 0;

        styleCombo(targetGroupBox);
        styleField(titleField);
        styleField(dateField);
        styleField(timeField);
        styleField(durationField);

        gc.gridx = 0; gc.weightx = 1;
        metaGrid.add(labeledField("Target Group", targetGroupBox), gc);
        gc.gridx = 1; gc.weightx = 1;
        metaGrid.add(labeledField("Quiz Title", titleField), gc);

        gc.gridy = 1; gc.gridx = 0; gc.weightx = 1;
        metaGrid.add(labeledField("Scheduled Date (YYYY-MM-DD)", dateField), gc);
        gc.gridx = 1; gc.weightx = 1;
        metaGrid.add(labeledField("Start Time (24h, HH:MM)", timeField), gc);

        gc.gridy = 2; gc.gridx = 0; gc.weightx = 1; gc.gridwidth = 1;
        metaGrid.add(labeledField("Duration (minutes)", durationField), gc);

        questionMatrix.setLayout(new BoxLayout(questionMatrix, BoxLayout.Y_AXIS));
        questionMatrix.setOpaque(false);
        JScrollPane matrixScroll = new JScrollPane(questionMatrix);
        matrixScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE, 1, true));
        matrixScroll.getViewport().setOpaque(false);
        matrixScroll.setOpaque(false);
        matrixScroll.setPreferredSize(new Dimension(0, 240));
        matrixScroll.getVerticalScrollBar().setUnitIncrement(12);

        // Green - non-destructive, adds a row.
        JButton addQuestionBtn = Buttons.primary("+ Add Question");
        addQuestionBtn.addActionListener(e -> addQuestionRow());

        JButton createBtn = Buttons.primary("Create & Schedule Quiz");
        createBtn.addActionListener(e -> submitNewQuiz());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(12, 0, 0, 0));
        actions.add(addQuestionBtn, BorderLayout.WEST);
        actions.add(createBtn, BorderLayout.EAST);

        card.add(formTitle, BorderLayout.NORTH);
        JPanel innerBody = new JPanel(new BorderLayout(0, 12));
        innerBody.setOpaque(false);
        innerBody.add(metaGrid, BorderLayout.NORTH);
        innerBody.add(matrixScroll, BorderLayout.CENTER);
        innerBody.add(actions, BorderLayout.SOUTH);
        card.add(innerBody, BorderLayout.CENTER);

        addQuestionRow();
        return card;
    }

    private JPanel labeledField(String label, JComponent field) {
        JPanel col = new JPanel(new BorderLayout(0, 5));
        col.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        col.add(l, BorderLayout.NORTH);
        col.add(field, BorderLayout.CENTER);
        return col;
    }

    /** One row of the question matrix: text + 4 options + correct-option pick + marks. */
    private final class QuestionRow {
        final JTextField text = new JTextField();
        final JTextField optA = new JTextField();
        final JTextField optB = new JTextField();
        final JTextField optC = new JTextField();
        final JTextField optD = new JTextField();
        final JComboBox<String> correct = new JComboBox<>(new String[]{"A", "B", "C", "D"});
        final JTextField marks = new JTextField("1");
        final JPanel container = roundedPanel(Theme.WHITE, null);
    }

    private void addQuestionRow() {
        QuestionRow row = new QuestionRow();
        row.container.setLayout(new GridBagLayout());
        row.container.setBorder(new EmptyBorder(14, 14, 14, 14));

        styleField(row.text);
        styleField(row.optA);
        styleField(row.optB);
        styleField(row.optC);
        styleField(row.optD);
        styleField(row.marks);
        styleCombo(row.correct);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridy = 0; gc.gridx = 0; gc.gridwidth = 4; gc.weightx = 1;
        row.container.add(labeledField("Question " + (questionRows.size() + 1), row.text), gc);

        gc.gridy = 1; gc.gridwidth = 1;
        gc.gridx = 0; row.container.add(labeledField("Option A", row.optA), gc);
        gc.gridx = 1; row.container.add(labeledField("Option B", row.optB), gc);
        gc.gridx = 2; row.container.add(labeledField("Option C", row.optC), gc);
        gc.gridx = 3; row.container.add(labeledField("Option D", row.optD), gc);

        gc.gridy = 2;
        gc.gridx = 0; row.container.add(labeledField("Correct", row.correct), gc);
        gc.gridx = 1; row.container.add(labeledField("Marks", row.marks), gc);

        // Red - destructive action, removes this question row.
        JButton removeBtn = Buttons.danger("Remove");
        gc.gridx = 3; gc.anchor = GridBagConstraints.EAST;
        row.container.add(removeBtn, gc);
        removeBtn.addActionListener(e -> {
            questionMatrix.remove(row.container);
            questionRows.remove(row);
            questionMatrix.revalidate();
            questionMatrix.repaint();
        });

        questionRows.add(row);
        questionMatrix.add(row.container);
        questionMatrix.add(Box.createVerticalStrut(10));
        questionMatrix.revalidate();
        questionMatrix.repaint();
    }

    private void submitNewQuiz() {
        GroupChoice group = (GroupChoice) targetGroupBox.getSelectedItem();
        if (group == null) {
            JOptionPane.showMessageDialog(this, "Pick a target group first.");
            return;
        }
        if (titleField.getText().isBlank() || questionRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add a title and at least one question first.");
            return;
        }

        JSONArray questions = new JSONArray();
        for (QuestionRow r : questionRows) {
            JSONObject q = new JSONObject()
                    .put("question_text", r.text.getText().trim())
                    .put("option_a", r.optA.getText().trim())
                    .put("option_b", r.optB.getText().trim())
                    .put("option_c", r.optC.getText().trim())
                    .put("option_d", r.optD.getText().trim())
                    .put("correct_option", (String) r.correct.getSelectedItem());
            try {
                q.put("marks", Integer.parseInt(r.marks.getText().trim()));
            } catch (NumberFormatException nfe) {
                q.put("marks", 1);
            }
            questions.put(q);
        }

        JSONObject payload = new JSONObject()
                .put("title", titleField.getText().trim())
                .put("scheduled_date", dateField.getText().trim())
                .put("start_time", timeField.getText().trim())
                .put("duration_minutes", parseIntOr(durationField.getText(), 30))
                .put("questions", questions);

        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.createQuiz(group.groupId(), payload);
                } catch (ApiOfflineException e) {
                    error = "Creating a quiz needs an internet connection.";
                } catch (ApiException e) {
                    error = "Failed to save. Check that every question row is filled in and start time is HH:MM (e.g. 14:00).";
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(QuizListPanel.this, error, "Couldn't create quiz", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                JOptionPane.showMessageDialog(QuizListPanel.this,
                        "Quiz scheduled with " + questions.length() + " question(s). It will open automatically at the scheduled time.");
                titleField.setText("");
                dateField.setText("");
                timeField.setText("");
                durationField.setText("30");
                questionMatrix.removeAll();
                questionRows.clear();
                addQuestionRow();
                questionMatrix.revalidate();
                questionMatrix.repaint();
                refreshManaged();
            }
        }.execute();
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // Quiz list (both modes use /me/quizzes)
    // ------------------------------------------------------------------

    /** Student entry point: quizzes across all their groups. */
    public void refreshMine() {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    return ctx.api.myQuizzes();
                } catch (ApiException | ApiOfflineException e) {
                    return new JSONArray();
                }
            }

            @Override
            protected void done() {
                try {
                    render(get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    /** Lecturer entry point: also refreshes the target-group dropdown for the create-quiz form. */
    public void refreshManaged() {
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
                GroupChoice previous = (GroupChoice) targetGroupBox.getSelectedItem();
                targetGroupBox.removeAllItems();
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject g = groups.getJSONObject(i);
                    if (g.optBoolean("is_member", false) || g.optBoolean("is_owner", false) || g.optBoolean("is_group_admin", false)) {
                        targetGroupBox.addItem(new GroupChoice(g.getLong("group_id"), g.optString("name", "Group")));
                    }
                }
                if (previous != null) targetGroupBox.setSelectedItem(previous);
                refreshMine();
            }
        }.execute();
    }

    private void render(JSONArray quizzes) {
        quizListBody.removeAll();
        if (quizzes.isEmpty()) {
            JLabel empty = new JLabel(canManage ? "You haven't created any quizzes yet." : "No quizzes yet.");
            empty.setForeground(Theme.MUTED);
            empty.setFont(Theme.BODY_FONT);
            empty.setBorder(new EmptyBorder(12, 4, 12, 4));
            quizListBody.add(empty);
        }
        int failed = 0;
        for (int i = 0; i < quizzes.length(); i++) {
            try {
                JSONObject quiz = quizzes.getJSONObject(i);
                quizListBody.add(quizRow(quiz));
                quizListBody.add(Box.createVerticalStrut(12));
            } catch (Exception e) {
                failed++;
                e.printStackTrace();
            }
        }
        if (failed > 0) {
            JLabel warn = new JLabel(failed + " quiz(zes) couldn't be displayed \u2013 check console for details.");
            warn.setForeground(Theme.WARN);
            quizListBody.add(warn);
        }
        quizListBody.revalidate();
        quizListBody.repaint();
    }

    private JComponent quizRow(JSONObject quiz) {
        String status = quiz.optString("status", "Draft");
        Color statusColor = switch (status) {
            case "Open" -> new Color(0x2E7D32);
            case "Closed" -> Theme.MUTED;
            case "Scheduled" -> Theme.SKY;
            default -> Theme.WARN;
        };

        JPanel row = roundedPanel(Theme.WHITE, statusColor);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(14, 20, 14, 16));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel titleLbl = new JLabel(quiz.optString("title", "Untitled quiz"));
        titleLbl.setFont(Theme.BODY_FONT_BOLD.deriveFont(15f));
        titleLbl.setForeground(Theme.INK);
        left.add(titleLbl);
        left.add(Box.createVerticalStrut(3));

        JLabel statusLabel = new JLabel(status);
        statusLabel.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        statusLabel.setForeground(statusColor);
        left.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        if (canManage) {
            if ("Scheduled".equals(status) || "Draft".equals(status)) {
                JButton publish = Buttons.primary("Publish");
                publish.addActionListener(e -> manage(() -> ctx.api.publishQuiz(quiz.getLong("quiz_id"))));
                right.add(publish);
            }
            if ("Open".equals(status)) {
                // Green - matches the rest of the action buttons on this row.
                JButton close = Buttons.primary("Close");
                close.addActionListener(e -> manage(() -> ctx.api.closeQuiz(quiz.getLong("quiz_id"))));
                right.add(close);
            }
            // Green - just a view action, nothing destructive.
            JButton results = Buttons.primary("Results");
            results.addActionListener(e -> showResults(quiz.getLong("quiz_id"), quiz.optString("title")));
            right.add(results);
        } else if ("Open".equals(status)) {
            JButton attempt = Buttons.primary("Begin Quiz");
            attempt.addActionListener(e -> new QuizAttemptDialog(SwingUtilities.getWindowAncestor(this), ctx, quiz.getLong("quiz_id")).setVisible(true));
            right.add(attempt);
        }

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private interface ApiCall {
        JSONObject run() throws ApiException, ApiOfflineException;
    }

    private void manage(ApiCall call) {
        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    call.run();
                } catch (ApiOfflineException e) {
                    error = "This action needs an internet connection.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(QuizListPanel.this, error, "Quiz action failed", JOptionPane.WARNING_MESSAGE);
                }
                if (canManage) refreshManaged(); else refreshMine();
            }
        }.execute();
    }

    private void showResults(long quizId, String title) {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                try {
                    return ctx.api.quizResults(quizId);
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONArray attempts;
                try {
                    attempts = get();
                } catch (Exception e) {
                    attempts = null;
                }
                if (attempts == null) {
                    JOptionPane.showMessageDialog(QuizListPanel.this, "Results need an internet connection.");
                    return;
                }
                String[] cols = {"Student", "Score", "Submitted"};
                Object[][] rows = new Object[attempts.length()][3];
                for (int i = 0; i < attempts.length(); i++) {
                    JSONObject a = attempts.getJSONObject(i);
                    JSONObject user = a.optJSONObject("user");
                    rows[i][0] = user != null ? user.optString("full_name") : "Student";
                    rows[i][1] = a.opt("score");
                    rows[i][2] = a.optString("submitted_at", "\u2013");
                }
                JTable table = new JTable(rows, cols);
                JOptionPane.showMessageDialog(QuizListPanel.this, new JScrollPane(table), "Results \u2013 " + title, JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();
    }
}