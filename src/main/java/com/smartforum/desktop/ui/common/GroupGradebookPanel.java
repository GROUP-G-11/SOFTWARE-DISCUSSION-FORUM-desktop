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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Full-page version of the old GradebookDialog, styled to match the
 * Laravel web client's gradebook card (resources/views/dashboard/*.blade.php
 * gradebookViewHtml()): a bordered white card with a bold header row,
 * subtle striped rows, and a bold "Overall total" column - rather than a
 * bare default JTable.
 */
public class GroupGradebookPanel extends JPanel {

    private static final Color BORDER_COLOR = new Color(0xE2E8F0);
    private static final Color STRIPE_COLOR = new Color(0xF8FAFC);

    private final AppContext ctx;
    private final JLabel title = new JLabel("Loading gradebook\u2026");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Student", "Participation", "Quiz score", "# quizzes taken", "Overall total"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    public GroupGradebookPanel(AppContext ctx, Runnable onBack) {
        this.ctx = ctx;
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Theme.WHITE);

        JButton back = Buttons.link("\u2190 Back to Groups", Theme.SKY);
        back.addActionListener(e -> onBack.run());

        title.setFont(Theme.HEADING_FONT.deriveFont(24f));
        title.setForeground(Theme.INK);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backRow.setOpaque(false);
        backRow.add(back);
        header.add(backRow);
        header.add(Box.createVerticalStrut(8));
        header.add(title);

        styleTable();

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Theme.WHITE);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(0, 0, 0, 0)));
        card.add(table.getTableHeader(), BorderLayout.NORTH);
        card.add(table, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);
    }

    private void styleTable() {
        table.setRowHeight(44);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setFont(Theme.BODY_FONT);
        table.setForeground(Theme.INK);
        table.setBackground(Theme.WHITE);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setBorder(null);

        JTableHeader tableHeader = table.getTableHeader();
        tableHeader.setFont(Theme.BODY_FONT_BOLD.deriveFont(13f));
        tableHeader.setForeground(Theme.INK);
        tableHeader.setBackground(Theme.WHITE);
        tableHeader.setPreferredSize(new Dimension(0, 44));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_COLOR));
        tableHeader.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, column);
                label.setBorder(new EmptyBorder(0, 16, 0, 16));
                label.setFont(Theme.BODY_FONT_BOLD.deriveFont(13f));
                label.setForeground(Theme.INK);
                label.setBackground(Theme.WHITE);
                label.setOpaque(true);
                return label;
            }
        });

        // Striped rows + bottom hairline per row (matches Laravel's
        // divide-y row borders), bold text only in the "Overall total"
        // column, everything else regular weight.
        DefaultTableCellRenderer rowRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, column);
                boolean isLastColumn = column == t.getColumnCount() - 1;
                label.setFont(isLastColumn ? Theme.BODY_FONT_BOLD : Theme.BODY_FONT);
                label.setForeground(Theme.INK);
                label.setBackground(row % 2 == 0 ? Theme.WHITE : STRIPE_COLOR);
                label.setOpaque(true);
                label.setBorder(new CompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                        new EmptyBorder(0, 16, 0, 16)));
                return label;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(rowRenderer);
        }
    }

    public void open(long groupId) {
        title.setText("Loading gradebook\u2026");
        model.setRowCount(0);

        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() {
                try {
                    return ctx.api.gradebook(groupId);
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
                if (result == null) {
                    title.setText("Gradebook");
                    model.setDataVector(
                            new Object[][]{{"Could not load the gradebook (are you the lecturer for this group?).", "", "", "", ""}},
                            new Object[]{"Student", "Participation", "Quiz score", "# quizzes taken", "Overall total"});
                    return;
                }
                title.setText(result.optString("group", "Group") + " \u2014 Gradebook");
                JSONArray rows = result.optJSONArray("rows", new JSONArray());
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    model.addRow(new Object[]{
                            r.optString("full_name", "Student"),
                            formatNumber(r.opt("participation_total")),
                            formatNumber(r.opt("quiz_total")),
                            r.opt("quiz_attempts_count"),
                            formatNumber(r.opt("overall_total"))
                    });
                }
            }
        }.execute();
    }

    /** Laravel formats these as 2-decimal numbers (e.g. "0.00", "1.00"). */
    private String formatNumber(Object value) {
        if (value == null) return "0.00";
        try {
            double d = Double.parseDouble(String.valueOf(value));
            return String.format("%.2f", d);
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
    }
}