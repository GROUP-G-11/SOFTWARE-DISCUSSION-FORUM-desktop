package com.smartforum.desktop.ui.common;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/** Thin strip pinned to the bottom of the sidebar: connection state + any actions still queued for sync. */
public class StatusBar extends JPanel {

    private final JLabel dot = new JLabel("\u25CF"); // filled circle
    private final JLabel label = new JLabel("Online");

    public StatusBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setOpaque(false);
        dot.setForeground(Theme.ACCENT.brighter());
        label.setForeground(Theme.PAPER_DIM);
        label.setFont(Theme.SMALL_FONT);
        add(dot);
        add(label);
    }

    public void setOnline(boolean online, int pendingOutbox) {
        dot.setForeground(online ? new java.awt.Color(0x4CAF50) : Theme.WARN.brighter());
        if (online) {
            label.setText(pendingOutbox > 0 ? "Online \u2013 syncing " + pendingOutbox + " item(s)" : "Online");
        } else {
            label.setText(pendingOutbox > 0 ? "Offline \u2013 " + pendingOutbox + " queued" : "Offline \u2013 showing cached data");
        }
    }
}
