package com.smartforum.desktop.ui.common;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;

public final class Buttons {

    private Buttons() {
    }

    public static JButton primary(String text) {
        JButton b = new JButton(text);
        style(b, Theme.ACCENT, Theme.WHITE, Theme.ACCENT_DARK);
        return b;
    }

    public static JButton danger(String text) {
        JButton b = new JButton(text);
        style(b, Theme.WARN, Theme.WHITE, Theme.WARN.darker());
        return b;
    }

    public static JButton secondary(String text) {
        JButton b = new JButton(text);
        b.setFont(Theme.BODY_FONT_BOLD);
        b.setForeground(Theme.INK);
        b.setBackground(Theme.PAPER_DIM);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        return b;
    }

    private static void style(JButton b, Color bg, Color fg, Color hover) {
        b.setFont(Theme.BODY_FONT_BOLD);
        b.setForeground(fg);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Small rounded label used for status badges like JOINED / Open / Closed. */
    public static JLabel pill(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setFont(Theme.SMALL_FONT.deriveFont(java.awt.Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        return label;
    }
    public static JButton link(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(Theme.SMALL_FONT.deriveFont(java.awt.Font.BOLD));
        b.setForeground(color);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
