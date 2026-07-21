package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public class CreateTopicDialog extends JDialog {

    private final JTextField titleField = new JTextField();
    private final JLabel errorLabel = new JLabel(" ");

    public CreateTopicDialog(Window owner, AppContext ctx, long groupId, Consumer<Boolean> onDone) {
        super(owner, "Start a new topic", ModalityType.APPLICATION_MODAL);
        setResizable(false);
        getContentPane().setBackground(Theme.WHITE);
        setLayout(new BorderLayout());

        JPanel card = new JPanel(new BorderLayout(0, 18));
        card.setBackground(Theme.WHITE);
        card.setBorder(new EmptyBorder(26, 28, 22, 28));

        JLabel title = new JLabel("Start a new topic");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel fieldLabel = new JLabel("Topic title");
        fieldLabel.setFont(Theme.FIELD_LABEL_FONT);
        fieldLabel.setForeground(Theme.INK);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(fieldLabel);

        form.add(Box.createVerticalStrut(6));
        titleField.setFont(Theme.BODY_FONT);
        titleField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        titleField.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        form.add(titleField);

        errorLabel.setForeground(Theme.WARN);
        errorLabel.setFont(Theme.SMALL_FONT);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(10));
        form.add(errorLabel);

        card.add(form, BorderLayout.CENTER);

        JButton cancel = Buttons.secondary("Cancel");
        cancel.addActionListener(e -> dispose());

        JButton create = Buttons.primary("Create topic");
        create.addActionListener(e -> submit(ctx, groupId, onDone));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(cancel);
        footer.add(create);
        card.add(footer, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
        setSize(420, 260);
        setLocationRelativeTo(owner);
    }

    private void submit(AppContext ctx, long groupId, Consumer<Boolean> onDone) {
        String title = titleField.getText().trim();
        if (title.isBlank()) {
            errorLabel.setText("Topic title is required.");
            return;
        }

        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.createTopic(groupId, title);
                } catch (ApiOfflineException e) {
                    error = "Creating a new topic needs an internet connection. Please try again once you're back online.";
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    errorLabel.setText(error);
                } else {
                    dispose();
                    if (onDone != null) onDone.accept(true);
                }
            }
        }.execute();
    }
}