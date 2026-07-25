package com.smartforum.desktop.ui.common;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Thin strip pinned to the bottom of the sidebar: connection state + any actions still queued for sync. */
public class StatusBar extends JPanel {

    // ===== DEBUG SWITCH =====
    // Set this to false once you've confirmed the text is visible, to go back
    // to the normal transparent look. While true, the label gets a loud
    // yellow background so it's impossible to miss, even at 0-contrast text color.
    private static final boolean DEBUG_HIGHLIGHT = false;

    private final JLabel dot = new JLabel("\u25CF"); // filled circle
    private final JLabel label = new JLabel("Online");
    private int lastFailedOutbox = 0;

    public StatusBar() {
        // BorderLayout instead of FlowLayout-inside-BoxLayout: dot pinned to the
        // left, label takes every remaining pixel of the row. No ambiguity about
        // alignment or whether the label is allowed to size itself.
        setLayout(new BorderLayout(8, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(4, 10, 4, 10));

        dot.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        dot.setForeground(Theme.ACCENT.brighter());
        dot.setPreferredSize(new Dimension(14, 20));

        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        label.setForeground(Theme.PAPER_DIM);
        label.setHorizontalAlignment(SwingConstants.LEFT);

        if (DEBUG_HIGHLIGHT) {
            label.setOpaque(true);
            label.setBackground(Color.YELLOW);
            label.setForeground(Color.BLACK);
        }

        add(dot, BorderLayout.WEST);
        add(label, BorderLayout.CENTER);

        // Hard floor so this row can never be squeezed to nothing by a parent
        // BoxLayout, regardless of what else is added around it.
        setPreferredSize(new Dimension(220, 26));
        setMinimumSize(new Dimension(140, 26));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
    }

    public void setOnline(boolean online, int pendingOutbox, int failedOutbox) {
        lastFailedOutbox = failedOutbox;

        if (!DEBUG_HIGHLIGHT) {
            java.awt.Color stateColor = online ? new java.awt.Color(0x4CAF50) : Theme.WARN.brighter();
            dot.setForeground(stateColor);
            label.setForeground(stateColor);
        } else {
            dot.setForeground(online ? new java.awt.Color(0x4CAF50) : Theme.WARN.brighter());
        }

        if (failedOutbox > 0) {
            label.setText(failedOutbox + " item(s) need attention" + (pendingOutbox > 0 ? " \u00b7 " + pendingOutbox + " queued" : "") + " (click to dismiss)");
        } else if (online) {
            label.setText(pendingOutbox > 0 ? "Online \u2013 syncing " + pendingOutbox + " item(s)" : "Online");
        } else {
            label.setText("Offline");
        }

        revalidate();
        repaint();

        System.out.println("[StatusBar] text=[" + label.getText() + "] labelSize=" + label.getSize()
                + " labelVisible=" + label.isVisible() + " statusBarSize=" + getSize());
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