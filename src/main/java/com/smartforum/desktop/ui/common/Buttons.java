package com.smartforum.desktop.ui.common;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public final class Buttons {

    private Buttons() {
    }

    public static JButton primary(String text) {
        return new RoundedButton(text, Theme.ACCENT, Theme.WHITE, Theme.ACCENT_HOVER, null);
    }

    public static JButton danger(String text) {
        return new RoundedButton(text, Theme.WARN, Theme.WHITE, Theme.WARN_HOVER, null);
    }

    public static JButton secondary(String text) {
        return new RoundedButton(text, Theme.PAPER_DIM, Theme.INK, Theme.PAPER_DIM_HOVER, Theme.LINE);
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

    /**
     * Stadium/pill-shaped button: fully rounded ends (arc = height, so the
     * left/right edges are perfect semicircles like the web CSS buttons),
     * an optional outline (used by "secondary" so it stays visible against
     * a white/paper page background), and a hover tint so mouse-over gives
     * real feedback instead of just a cursor change.
     */
    private static class RoundedButton extends JButton {
        private final Color base;
        private final Color hover;
        private final Color outline;
        private boolean hovering = false;

        RoundedButton(String text, Color base, Color fg, Color hover, Color outline) {
            super(text);
            this.base = base;
            this.hover = hover;
            this.outline = outline;
            setForeground(fg);
            setFont(Theme.BODY_FONT_BOLD);
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = getHeight();
            g2.setColor(!isEnabled() ? base.brighter() : (hovering ? hover : base));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            if (outline != null) {
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
