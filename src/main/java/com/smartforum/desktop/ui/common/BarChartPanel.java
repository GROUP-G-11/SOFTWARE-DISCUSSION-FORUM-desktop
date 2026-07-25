package com.smartforum.desktop.ui.common;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Lightweight, dependency-free bar chart. Mirrors the "Visual Charts" bar
 * chart Chart.js renders in dashboard/admin.blade.php (viewInlineGroupStats)
 * for group metrics - same 5 bars, same rough color palette - without
 * pulling in a charting library (no JFreeChart/etc. dependency exists in
 * pom.xml), so it paints itself directly onto a Swing canvas.
 */
public class BarChartPanel extends JPanel {

    private String[] labels = new String[0];
    private double[] values = new double[0];
    private Color[] colors = new Color[0];

    public BarChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(400, 250));
    }

    /** Replaces the chart's data and repaints. Arrays must be the same length. */
    public void setData(String[] labels, double[] values, Color[] colors) {
        this.labels = labels != null ? labels : new String[0];
        this.values = values != null ? values : new double[0];
        this.colors = colors != null ? colors : new Color[0];
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (labels.length == 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.SMALL_FONT);
            String msg = "No data to chart yet.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        int leftPad = 44;
        int rightPad = 16;
        int topPad = 16;
        int bottomPad = 46;
        int plotW = Math.max(10, w - leftPad - rightPad);
        int plotH = Math.max(10, h - topPad - bottomPad);

        double max = 0;
        for (double v : values) {
            max = Math.max(max, v);
        }
        // Give the tallest bar a little headroom above it, and never divide by zero.
        double axisMax = max <= 0 ? 1 : niceCeiling(max);

        int baseY = topPad + plotH;

        // Gridlines + y-axis labels (4 steps, matching Chart.js's default feel).
        g2.setFont(Theme.SMALL_FONT);
        g2.setColor(Theme.LINE);
        FontMetrics fm = g2.getFontMetrics();
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            double val = axisMax * i / steps;
            int y = baseY - (int) Math.round(plotH * (val / axisMax));
            g2.setColor(Theme.LINE);
            g2.drawLine(leftPad, y, leftPad + plotW, y);
            g2.setColor(Theme.MUTED);
            String label = formatAxisValue(val);
            g2.drawString(label, leftPad - fm.stringWidth(label) - 6, y + fm.getAscent() / 2 - 2);
        }

        // Bars.
        int n = labels.length;
        int slot = plotW / n;
        int barW = Math.max(18, (int) (slot * 0.55));
        g2.setStroke(new BasicStroke(1.2f));

        for (int i = 0; i < n; i++) {
            double v = i < values.length ? values[i] : 0;
            Color c = i < colors.length && colors[i] != null ? colors[i] : Theme.ACCENT;
            int barH = (int) Math.round(plotH * (v / axisMax));
            int x = leftPad + i * slot + (slot - barW) / 2;
            int y = baseY - barH;

            g2.setColor(c);
            g2.fillRoundRect(x, y, barW, Math.max(barH, 1), 6, 6);
            g2.setColor(c.darker());
            g2.drawRoundRect(x, y, barW, Math.max(barH, 1), 6, 6);

            // Value on top of the bar.
            g2.setColor(Theme.INK);
            g2.setFont(Theme.SMALL_FONT.deriveFont(java.awt.Font.BOLD));
            String valText = formatAxisValue(v);
            FontMetrics vfm = g2.getFontMetrics();
            g2.drawString(valText, x + (barW - vfm.stringWidth(valText)) / 2, y - 4);

            // Wrapped label under the axis.
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.SMALL_FONT);
            drawWrappedLabel(g2, labels[i], x + barW / 2, baseY + 14, slot);
        }

        // Axis line.
        g2.setColor(Theme.INK);
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawLine(leftPad, baseY, leftPad + plotW, baseY);

        g2.dispose();
    }

    private void drawWrappedLabel(Graphics2D g2, String text, int centerX, int y, int maxWidth) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y + fm.getAscent();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) {
                g2.drawString(line.toString(), centerX - fm.stringWidth(line.toString()) / 2, lineY);
                line = new StringBuilder(word);
                lineY += fm.getHeight();
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            g2.drawString(line.toString(), centerX - fm.stringWidth(line.toString()) / 2, lineY);
        }
    }

    private String formatAxisValue(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) {
            return String.valueOf(Math.round(v));
        }
        return String.format("%.1f", v);
    }

    /** Rounds up to a "nice" number (1/2/5 * 10^n) so gridlines land on tidy values. */
    private double niceCeiling(double value) {
        double exponent = Math.floor(Math.log10(value));
        double magnitude = Math.pow(10, exponent);
        double residual = value / magnitude;
        double niceResidual;
        if (residual <= 1) niceResidual = 1;
        else if (residual <= 2) niceResidual = 2;
        else if (residual <= 5) niceResidual = 5;
        else niceResidual = 10;
        return niceResidual * magnitude;
    }
}

