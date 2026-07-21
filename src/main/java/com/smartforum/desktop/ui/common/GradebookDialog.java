package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class GradebookDialog extends JDialog {

    public GradebookDialog(Window owner, AppContext ctx, long groupId) {
        super(owner, "Gradebook", ModalityType.APPLICATION_MODAL);
        setSize(640, 480);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.WHITE);

        JLabel title = new JLabel("Loading gradebook\u2026");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);
        title.setBorder(new EmptyBorder(0, 0, 12, 0));

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Student", "Participation", "Quiz score", "# quizzes taken", "Overall total"}, 0);
        JTable table = new JTable(model);
        table.setRowHeight(26);

        setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(20, 24, 20, 24));
        add(title, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

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
                    model.setDataVector(new Object[][]{{"Could not load the gradebook (are you the lecturer for this group?).", "", "", "", ""}},
                            new Object[]{"Student", "Participation", "Quiz score", "# quizzes taken", "Overall total"});
                    return;
                }
                title.setText(result.optString("group", "Group") + " \u2014 Gradebook");
                JSONArray rows = result.optJSONArray("rows", new JSONArray());
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    model.addRow(new Object[]{
                            r.optString("full_name", "Student"),
                            r.opt("participation_total"),
                            r.opt("quiz_total"),
                            r.opt("quiz_attempts_count"),
                            r.opt("overall_total")
                    });
                }
            }
        }.execute();
    }
}
