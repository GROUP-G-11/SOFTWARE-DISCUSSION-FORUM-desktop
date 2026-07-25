package com.smartforum.desktop.ui.common;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Thin strip pinned to the bottom of the sidebar: connection state + any actions still queued for sync. */
public class StatusBar extends JPanel {

    private final JLabel dot = new JLabel("\u25CF"); // filled circle
    private final JLabel label = new JLabel("Online");
    private int lastFailedOutbox = 0;

    public StatusBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setOpaque(false);
        dot.setForeground(Theme.ACCENT.brighter());
        label.setForeground(Theme.PAPER_DIM);
        label.setFont(Theme.SMALL_FONT);
        add(dot);
        add(label);
    }

    public void setOnline(boolean online, int pendingOutbox, int failedOutbox) {
        lastFailedOutbox = failedOutbox;
        dot.setForeground(online ? new java.awt.Color(0x4CAF50) : Theme.WARN.brighter());
        if (failedOutbox > 0) {
            label.setText(failedOutbox + " item(s) need attention" + (pendingOutbox > 0 ? " \u00b7 " + pendingOutbox + " queued" : "") + " (click to dismiss)");
        } else if (online) {
            label.setText(pendingOutbox > 0 ? "Online \u2013 syncing " + pendingOutbox + " item(s)" : "Online");
        } else {
            label.setText(pendingOutbox > 0 ? "Offline \u2013 " + pendingOutbox + " queued" : "Offline \u2013 showing cached data");
        }
    }

    /** Lets the person click the status line to clear a stuck failed-item count instead of it accumulating forever. */
    public void setOnDismissFailed(Runnable action) {
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (lastFailedOutbox > 0) action.run();
            }
        });
    }
}