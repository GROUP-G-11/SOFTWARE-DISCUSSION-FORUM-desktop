package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;
import com.smartforum.desktop.api.ApiException;
import com.smartforum.desktop.api.ApiOfflineException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CreateGroupDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JTextArea descField = new JTextArea(4, 24);
    private final JLabel errorLabel = new JLabel(" ");

    public CreateGroupDialog(Window owner, AppContext ctx, Runnable onCreated) {
        super(owner, "Create Group", ModalityType.APPLICATION_MODAL);
        setResizable(false);
        getContentPane().setBackground(Theme.WHITE);
        setLayout(new BorderLayout());

        JPanel card = new JPanel(new BorderLayout(0, 18));
        card.setBackground(Theme.WHITE);
        card.setBorder(new EmptyBorder(26, 28, 22, 28));

        JLabel title = new JLabel("Create a new group");
        title.setFont(Theme.HEADING_FONT_SM);
        title.setForeground(Theme.INK);
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(fieldLabel("Group Name"));
        form.add(Box.createVerticalStrut(6));
        nameField.setFont(Theme.BODY_FONT);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        form.add(nameField);

        form.add(Box.createVerticalStrut(16));
        form.add(fieldLabel("Description"));
        form.add(Box.createVerticalStrut(6));
        descField.setFont(Theme.BODY_FONT);
        descField.setLineWrap(true);
        descField.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descField);
        descScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE, 1, true));
        descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(descScroll);

        errorLabel.setForeground(Theme.WARN);
        errorLabel.setFont(Theme.SMALL_FONT);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(10));
        form.add(errorLabel);

        card.add(form, BorderLayout.CENTER);

        JButton cancel = Buttons.secondary("Cancel");
        cancel.addActionListener(e -> dispose());

        JButton create = Buttons.primary("Create group");
        create.addActionListener(e -> submit(ctx, onCreated));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(cancel);
        footer.add(create);
        card.add(footer, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
        setSize(460, 420);
        setLocationRelativeTo(owner);   // <-- THIS is what centers the popup over your app window
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FIELD_LABEL_FONT);
        l.setForeground(Theme.INK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void submit(AppContext ctx, Runnable onCreated) {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            errorLabel.setText("Group name is required.");
            return;
        }
        String desc = descField.getText().trim();

        new SwingWorker<Void, Void>() {
            String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    ctx.api.createGroup(name, desc, null, null);
                } catch (ApiOfflineException e) {
                    error = "Creating a group needs an internet connection.";
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
                    if (onCreated != null) onCreated.run();
                }
            }
        }.execute();
    }
}