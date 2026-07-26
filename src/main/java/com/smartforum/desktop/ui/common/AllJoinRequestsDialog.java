package com.smartforum.desktop.ui.common;

import com.smartforum.desktop.AppContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;


import java.awt.*;
import java.util.List;

public class AllJoinRequestsDialog extends JDialog {

    private final AppContext ctx;
    private final List<Long> managedGroupIds;
    private final List<String> managedGroupNames;

    private JPanel requestsPanel;
    

    public AllJoinRequestsDialog(
            Window owner,
            AppContext ctx,
            List<Long> managedGroupIds,
            List<String> managedGroupNames
    ) {
        super(owner, "Join Requests", ModalityType.APPLICATION_MODAL);

        this.ctx = ctx;
        this.managedGroupIds = managedGroupIds;
        this.managedGroupNames = managedGroupNames;

        initialize();
    }

    private void loadRequests() {

    requestsPanel.removeAll();

    for (int i = 0; i < managedGroupIds.size(); i++) {

        long groupId = managedGroupIds.get(i);
        String groupName = managedGroupNames.get(i);

        requestsPanel.add(createGroupRow(groupId, groupName));
        requestsPanel.add(Box.createVerticalStrut(10));
        requestsPanel.setBorder(new EmptyBorder(15,20,15,20));
    }

    requestsPanel.revalidate();
    requestsPanel.repaint();
}

private JPanel createGroupRow(long groupId, String groupName) {

    JPanel row = new JPanel(new BorderLayout(10, 10));
    row.setBorder(new EmptyBorder(10, 10, 10, 10));

    JLabel name = new JLabel(groupName);
    name.setFont(new Font("SansSerif", Font.BOLD, 16));

    JLabel subtitle = new JLabel("Select a group to review membership requests.");
    subtitle.setForeground(Color.GRAY);

    JButton viewButton = new JButton("View Requests");

    viewButton.addActionListener(e -> {

        new JoinRequestsDialog(
                this,
                ctx,
                groupId,
                groupName,
                this::loadRequests
        ).setVisible(true);

    });

    row.add(name, BorderLayout.CENTER);
    row.add(viewButton, BorderLayout.EAST);

    return row;
}

    private void initialize() {

        setSize(850, 600);
        setLocationRelativeTo(getOwner());

        setLayout(new BorderLayout());

        JLabel title = new JLabel("Pending Join Requests");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBorder(new EmptyBorder(15,15,15,15));

        add(title, BorderLayout.NORTH);

        requestsPanel = new JPanel();
        requestsPanel.setLayout(new BoxLayout(requestsPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(requestsPanel);

        add(scrollPane, BorderLayout.CENTER);

        JButton close = new JButton("Close");

        close.addActionListener(e -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);

        add(south, BorderLayout.SOUTH);

        loadRequests();
    }
}