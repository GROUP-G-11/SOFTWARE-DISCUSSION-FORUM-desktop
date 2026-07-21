package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Attempt Quiz use case (SDD Table 40). Deliberately hard to escape while
 * it's running: undecorated, modal, always-on-top, and window-closing is
 * swallowed - "prevent the user from doing other things" per the SDD. The
 * only ways out are submitting or the timer hitting zero (auto-submit).
 */
public class QuizAttemptDialog extends JDialog {

    private final AppContext ctx;
    private final long quizId;

    private long attemptId = -1;
    private JSONArray questions = new JSONArray();
    private int currentIndex = 0;
    private final Map<Long, String> selectedAnswers = new HashMap<>();

    private final JLabel questionCounter = new JLabel();
    private final JLabel questionText = new JLabel();
    private final ButtonGroup optionGroup = new ButtonGroup();
    private final JRadioButton[] optionButtons = new JRadioButton[4];
    private final JLabel timerLabel = new JLabel();
    private final JButton prevBtn = new JButton("Previous");
    private final JButton nextBtn = new JButton("Next");
    private final JButton submitBtn = new JButton("Submit");

    private javax.swing.Timer countdown;
    private int secondsRemaining = 0;
    private boolean submitted = false;

    public QuizAttemptDialog(Window owner, AppContext ctx, long quizId) {
        super(owner, "Quiz", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        this.quizId = quizId;

        setUndecorated(true);
        setAlwaysOnTop(true);
        setSize(640, 480);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.WHITE);
        ((JComponent) getContentPane()).setBorder(BorderFactory.createLineBorder(Theme.INK, 2));

        // Focus-lock: swallow the window-close gesture entirely while a
        // quiz is in progress - the only way out is Submit or timeout.
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // No-op on purpose.
            }
        });

        buildUi();
        startAttempt();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.INK);
        header.setBorder(new EmptyBorder(12, 18, 12, 18));
        JLabel lockedLabel = new JLabel("Quiz in progress \u2014 no other action is available until you submit");
        lockedLabel.setForeground(Theme.PAPER);
        lockedLabel.setFont(Theme.SMALL_FONT);
        timerLabel.setForeground(new Color(0xF0C36D));
        timerLabel.setFont(Theme.BODY_FONT_BOLD.deriveFont(15f));
        header.add(lockedLabel, BorderLayout.WEST);
        header.add(timerLabel, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(Theme.WHITE);
        body.setBorder(new EmptyBorder(20, 24, 20, 24));

        questionCounter.setFont(Theme.SMALL_FONT);
        questionCounter.setForeground(Color.GRAY);
        questionText.setFont(Theme.HEADING_FONT_SM);
        questionText.setForeground(Theme.INK);

        JPanel questionHeader = new JPanel();
        questionHeader.setOpaque(false);
        questionHeader.setLayout(new BoxLayout(questionHeader, BoxLayout.Y_AXIS));
        questionHeader.add(questionCounter);
        questionHeader.add(questionText);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setOpaque(false);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        String[] labels = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JRadioButton();
            optionButtons[i].setFont(Theme.BODY_FONT);
            optionButtons[i].setOpaque(false);
            optionGroup.add(optionButtons[i]);
            optionsPanel.add(optionButtons[i]);
            optionsPanel.add(Box.createVerticalStrut(6));
            final String opt = labels[i];
            optionButtons[i].addActionListener(e -> recordCurrentAnswer(opt));
        }

        body.add(questionHeader, BorderLayout.NORTH);
        body.add(optionsPanel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.WHITE);
        footer.setBorder(new EmptyBorder(0, 24, 20, 24));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        navButtons.setOpaque(false);
        prevBtn.addActionListener(e -> { saveCurrentSelection(); currentIndex--; renderQuestion(); });
        nextBtn.addActionListener(e -> { saveCurrentSelection(); currentIndex++; renderQuestion(); });
        prevBtn.setFont(Theme.BODY_FONT_BOLD);
        nextBtn.setFont(Theme.BODY_FONT_BOLD);
        navButtons.add(prevBtn);
        navButtons.add(nextBtn);

        submitBtn.setFont(Theme.BODY_FONT_BOLD);
        submitBtn.setForeground(Theme.WHITE);
        submitBtn.setBackground(Theme.WARN);
        submitBtn.setOpaque(true);
        submitBtn.setBorder(new EmptyBorder(8, 18, 8, 18));
        submitBtn.addActionListener(e -> { saveCurrentSelection(); confirmAndSubmit(false); });

        footer.add(navButtons, BorderLayout.WEST);
        footer.add(submitBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private void startAttempt() {
        questionText.setText("Loading quiz\u2026");
        new SwingWorker<JSONObject, Void>() {
            String error = null;

            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.startAttempt(quizId);
                } catch (ApiOfflineException e) {
                    error = "Starting a quiz needs an internet connection. Please reconnect and try again.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                JSONObject attempt;
                try {
                    attempt = get();
                } catch (Exception e) {
                    attempt = null;
                }
                if (attempt == null) {
                    JOptionPane.showMessageDialog(QuizAttemptDialog.this, error, "Couldn't start quiz", JOptionPane.WARNING_MESSAGE);
                    dispose();
                    return;
                }
                attemptId = attempt.optLong("attempt_id", -1);
                JSONObject quiz = attempt.optJSONObject("quiz");
                questions = quiz != null ? quiz.optJSONArray("questions", new JSONArray()) : new JSONArray();
                secondsRemaining = attempt.optInt("seconds_remaining", 0);
                currentIndex = 0;
                renderQuestion();
                startCountdown();
            }
        }.execute();
    }

    private void startCountdown() {
        updateTimerLabel();
        countdown = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            updateTimerLabel();
            if (secondsRemaining <= 0) {
                countdown.stop();
                confirmAndSubmit(true);
            }
        });
        countdown.start();
    }

    private void updateTimerLabel() {
        int m = Math.max(0, secondsRemaining) / 60;
        int s = Math.max(0, secondsRemaining) % 60;
        timerLabel.setText(String.format("%02d:%02d remaining", m, s));
    }

    private void renderQuestion() {
        if (questions.isEmpty()) {
            questionText.setText("This quiz has no questions.");
            return;
        }
        JSONObject q = questions.getJSONObject(currentIndex);
        questionCounter.setText("Question " + (currentIndex + 1) + " of " + questions.length());
        questionText.setText("<html><body style='width:480px'>" + q.optString("question_text", "") + "</body></html>");

        optionButtons[0].setText("A. " + q.optString("option_a", ""));
        optionButtons[1].setText("B. " + q.optString("option_b", ""));
        optionButtons[2].setText("C. " + q.optString("option_c", ""));
        optionButtons[3].setText("D. " + q.optString("option_d", ""));

        optionGroup.clearSelection();
        long questionId = q.optLong("question_id", -1);
        String prior = selectedAnswers.get(questionId);
        if (prior != null) {
            int idx = "ABCD".indexOf(prior);
            if (idx >= 0) optionButtons[idx].setSelected(true);
        }

        prevBtn.setEnabled(currentIndex > 0);
        nextBtn.setEnabled(currentIndex < questions.length() - 1);
    }

    private void recordCurrentAnswer(String option) {
        if (questions.isEmpty()) return;
        long questionId = questions.getJSONObject(currentIndex).optLong("question_id", -1);
        selectedAnswers.put(questionId, option);
    }

    private void saveCurrentSelection() {
        for (int i = 0; i < 4; i++) {
            if (optionButtons[i].isSelected()) {
                recordCurrentAnswer("ABCD".substring(i, i + 1));
                return;
            }
        }
    }

    private void confirmAndSubmit(boolean autoSubmitted) {
        if (submitted) return;
        if (!autoSubmitted) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Submit your answers now? This can't be undone.",
                    "Submit quiz", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        submitted = true;
        if (countdown != null) countdown.stop();

        JSONArray answers = new JSONArray();
        for (Map.Entry<Long, String> entry : selectedAnswers.entrySet()) {
            answers.put(new JSONObject().put("question_id", entry.getKey()).put("selected_option", entry.getValue()));
        }

        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.submitAttempt(attemptId, answers, autoSubmitted);
                } catch (ApiException | ApiOfflineException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                JSONObject result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = null;
                }
                if (result != null) {
                    JOptionPane.showMessageDialog(QuizAttemptDialog.this,
                            "Score: " + result.opt("score"),
                            autoSubmitted ? "Time's up \u2014 quiz auto-submitted" : "Quiz submitted",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(QuizAttemptDialog.this,
                            "Couldn't reach the server to submit. Please check your connection and try again from the quiz list.",
                            "Submission failed", JOptionPane.ERROR_MESSAGE);
                }
                dispose();
            }
        }.execute();
    }
}
