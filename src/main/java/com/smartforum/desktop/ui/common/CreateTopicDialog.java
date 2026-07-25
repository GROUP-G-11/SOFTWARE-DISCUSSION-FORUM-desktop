package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public class CreateTopicDialog extends JDialog {

    private final JTextField titleField = new JTextField();
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton create = Buttons.primary("Create topic");

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
        if (!create.isEnabled()) return; // already submitting - ignore a repeat click
        String title = titleField.getText().trim();
        if (title.isBlank()) {
            errorLabel.setText("Topic title is required.");
            return;
        }
        String clientRef = java.util.UUID.randomUUID().toString();

        create.setEnabled(false);
        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.createTopic(groupId, title, clientRef);
                } catch (ApiOfflineException e) {
                    JSONObject payload = new JSONObject().put("group_id", groupId).put("title", title).put("client_ref", clientRef);
                    long outboxId = ctx.store.queueOutboxAction("create_topic", payload);
                    ctx.store.cachePendingTopic(groupId, outboxId, payload, ctx.session.user());
                } catch (ApiException e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                create.setEnabled(true);
                if (error != null) {
                    errorLabel.setText(error);
                } else {
                    // Both the real server response and the offline-queued
                    // path leave a topic (real or pending) ready to show, so
                    // both close the dialog and refresh the topic list.
                    dispose();
                    if (onDone != null) onDone.accept(true);
                }
            }
        }.execute();
    }
}