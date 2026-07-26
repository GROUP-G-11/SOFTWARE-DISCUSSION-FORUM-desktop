package com.smartforum.desktop.ui.common;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class AvatarView extends JComponent {

    private final int diameter;
    private final Color fallbackBg;
    private final Color borderColor;
    private String initials = "?";
    private BufferedImage image; // null => draw initials instead

    public AvatarView(int diameter, Color fallbackBg, Color borderColor) {
        this.diameter = diameter;
        this.fallbackBg = fallbackBg;
        this.borderColor = borderColor;
        setPreferredSize(new Dimension(diameter, diameter));
        setMinimumSize(new Dimension(diameter, diameter));
        setMaximumSize(new Dimension(diameter, diameter));
        setAlignmentX(Component.CENTER_ALIGNMENT);
        setOpaque(false);
    }

    public void setInitials(String initials) {
        this.initials = (initials == null || initials.isBlank()) ? "?" : initials;
        this.image = null;
        repaint();
    }

    /** Show a locally-chosen file immediately, before it has been uploaded. */
    public void setImageFile(File file) {
        try {
            BufferedImage raw = ImageIO.read(file);
            if (raw != null) {
                this.image = raw;
                repaint();
            }
        } catch (IOException ignored) {
            // File chooser already filters to image extensions, so this
            // should be rare - just leave whatever was showing before.
        }
    }

    /** Fetch and show the user's saved avatar from the server, off the EDT. */
    public void loadFromUrl(String url) {
        if (url == null || url.isBlank()) return;
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try {
                    return ImageIO.read(new URL(url));
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    BufferedImage result = get();
                    if (result != null) {
                        image = result;
                        repaint();
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Ellipse2D circle = new Ellipse2D.Float(0, 0, diameter, diameter);
        g2.setClip(circle);

        if (image != null) {
            g2.drawImage(image, 0, 0, diameter, diameter, null);
        } else {
            g2.setColor(fallbackBg);
            g2.fill(circle);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.HEADING_FONT.deriveFont(Font.BOLD, diameter * 0.34f));
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(initials);
            int textHeight = fm.getAscent();
            g2.drawString(initials, (diameter - textWidth) / 2f, (diameter + textHeight) / 2f - fm.getDescent());
        }

        g2.setClip(null);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Float(1, 1, diameter - 2, diameter - 2));
        g2.dispose();
    }
}
