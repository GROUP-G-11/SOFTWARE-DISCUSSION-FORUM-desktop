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
 *   - Lecturer (canManage=true): matches dashboard/lecturer.blade.php's
 *     #panel-quizzes layout exactly - an inline "Create a new quiz" form
 *     (group picker, title, schedule fields, a dynamic question matrix)
 *     stacked above "Your quizzes" (also GET /me/quizzes, with
 *     Publish/Close/Results). The create form does NOT auto-publish on
 *     submit - QuizController::store already sets status='Scheduled', and
 *     the quiz opens itself at its scheduled time; forcing it to 'Open'
 *     immediately (as an earlier version of this panel did) would let
 *     students attempt it right away regardless of the configured
 *     schedule, which is wrong.
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
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JLabel title = new JLabel("Quizzes");
        title.setFont(Theme.HEADING_FONT);
        title.setForeground(Theme.INK);

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(title);

        JPanel center;
        if (canManage) {
            north.add(Box.createVerticalStrut(14));
            north.add(buildCreateForm());
            JLabel listTitle = new JLabel("Your quizzes");
            listTitle.setFont(Theme.HEADING_FONT_SM);
            listTitle.setForeground(Theme.INK);
            listTitle.setBorder(new EmptyBorder(20, 0, 8, 0));
            north.add(listTitle);
            center = quizListPanel();
        } else {
            center = quizListPanel();
        }

        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private JPanel quizListPanel() {
        quizListBody.setLayout(new BoxLayout(quizListBody, BoxLayout.Y_AXIS));
        quizListBody.setOpaque(false);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(new JScrollPane(quizListBody), BorderLayout.CENTER);
        return wrap;
    }

    // ------------------------------------------------------------------
    // Lecturer: inline "Create a new quiz" form
    // ------------------------------------------------------------------

    private JComponent buildCreateForm() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Theme.PAPER_DIM);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.SKY),
                new EmptyBorder(18, 18, 18, 18)));

        JLabel formTitle = new JLabel("Create a new quiz");
        formTitle.setFont(Theme.HEADING_FONT_SM);
        formTitle.setForeground(Theme.INK);

        JPanel metaGrid = new JPanel(new GridBagLayout());
        metaGrid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 6, 4, 6);
        gc.gridy = 0;

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
        questionMatrix.setBackground(Theme.WHITE);
        JScrollPane matrixScroll = new JScrollPane(questionMatrix);
        matrixScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        matrixScroll.setPreferredSize(new Dimension(0, 220));

        JButton addQuestionBtn = Buttons.secondary("+ Add Question");
        addQuestionBtn.addActionListener(e -> addQuestionRow());

        JButton createBtn = Buttons.primary("Create & Schedule Quiz");
        createBtn.addActionListener(e -> submitNewQuiz());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(10, 0, 0, 0));
        actions.add(addQuestionBtn, BorderLayout.WEST);
        actions.add(createBtn, BorderLayout.EAST);

        card.add(formTitle, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(metaGrid, BorderLayout.NORTH);
        body.add(matrixScroll, BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);

        addQuestionRow();
        return card;
    }

    private JPanel labeledField(String label, JComponent field) {
        JPanel col = new JPanel(new BorderLayout(0, 3));
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
        final JPanel container = new JPanel();
    }

    private void addQuestionRow() {
        QuestionRow row = new QuestionRow();
        row.container.setLayout(new GridBagLayout());
        row.container.setBackground(Theme.WHITE);
        row.container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(8, 6, 8, 6)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
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

        JButton removeBtn = Buttons.secondary("Remove");
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
                    // Matches the real submit handler exactly: create only.
                    // No auto-publish - the quiz is created with status
                    // 'Scheduled' server-side and opens itself at its
                    // scheduled time (or the lecturer can Publish it early
                    // from the list below, same as the web client).
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
                } catch (Exception ignored) {
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
            empty.setForeground(Color.GRAY);
            quizListBody.add(empty);
        }
        for (int i = 0; i < quizzes.length(); i++) {
            quizListBody.add(quizRow(quizzes.getJSONObject(i)));
            quizListBody.add(Box.createVerticalStrut(1));
        }
        quizListBody.revalidate();
        quizListBody.repaint();
    }

    private JComponent quizRow(JSONObject quiz) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(12, 4, 12, 4)));
        row.setBackground(Theme.WHITE);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(quiz.optString("title", "Untitled quiz"));
        title.setFont(Theme.BODY_FONT_BOLD.deriveFont(14f));
        left.add(title);

        String status = quiz.optString("status", "Draft");
        Color statusColor = switch (status) {
            case "Open" -> new Color(0x2E7D32);
            case "Closed" -> Color.GRAY;
            case "Scheduled" -> Theme.SKY;
            default -> Theme.WARN;
        };
        JLabel statusLabel = new JLabel(status);
        statusLabel.setFont(Theme.SMALL_FONT);
        statusLabel.setForeground(statusColor);
        left.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        if (canManage) {
            if ("Scheduled".equals(status) || "Draft".equals(status)) {
                JButton publish = Buttons.secondary("Publish");
                publish.addActionListener(e -> manage(() -> ctx.api.publishQuiz(quiz.getLong("quiz_id"))));
                right.add(publish);
            }
            if ("Open".equals(status)) {
                JButton close = Buttons.secondary("Close");
                close.addActionListener(e -> manage(() -> ctx.api.closeQuiz(quiz.getLong("quiz_id"))));
                right.add(close);
            }
            JButton results = Buttons.secondary("Results");
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
            @Override
            protected Void doInBackground() {
                try {
                    call.run();
                } catch (ApiException | ApiOfflineException ignored) {
                }
                return null;
            }

            @Override
            protected void done() {
                if (canManage) refreshManaged(); else refreshMine();
            }
        }.execute();
    }

    private void showResults(long quizId, String title) {
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.quizResults(quizId);
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONObject results;
                try {
                    results = get();
                } catch (Exception e) {
                    results = null;
                }
                if (results == null) {
                    JOptionPane.showMessageDialog(QuizListPanel.this, "Results need an internet connection.");
                    return;
                }
                JSONArray attempts = results.optJSONArray("data", results.optJSONArray("attempts", new JSONArray()));
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
