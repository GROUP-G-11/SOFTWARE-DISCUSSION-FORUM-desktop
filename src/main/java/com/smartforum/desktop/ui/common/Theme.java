package com.smartforum.desktop.ui.common;

import java.awt.Color;
import java.awt.Font;

/**
 * Colors and fonts lifted directly from the Laravel web client's
 * {@code :root} CSS variables (resources/views/layouts/app.blade.php),
 * so the desktop client reads as the same application rather than a
 * reskinned generic Swing app.
 */
public final class Theme {

    private Theme() {
    }

    public static final Color INK = Color.decode("#1c2b33");
    public static final Color SIDEBAR_BOTTOM = Color.decode("#142027");
    public static final Color PAPER = Color.decode("#f6f4ee");
    public static final Color PAPER_DIM = Color.decode("#ece8db");
    public static final Color ACCENT = Color.decode("#2f6f5e");
    public static final Color ACCENT_DARK = Color.decode("#204b3f");
    public static final Color WARN = Color.decode("#b3542e");
    public static final Color LINE = Color.decode("#d8d2c4");
    public static final Color SKY = Color.decode("#2a5a72");
    public static final Color SKY_DIM = Color.decode("#e6edf1");
    public static final Color WHITE = Color.WHITE;
    public static final Color MUTED = Color.decode("#64748b");
    public static final Color BUBBLE_MINE = Color.decode("#dcf3d3");
    public static final int RADIUS = 6;

    // The web client uses a serif display font for headings (Iowan Old
    // Style/Georgia) and the system sans stack for everything else. Swing
    // can't load web fonts, so we fall back to the closest cross-platform
    // equivalents: Serif for headings, the platform sans for body text.
    // Sizes are deliberately generous (matching the previous desktop
    // client's scale) so the UI reads as well-spaced rather than cramped.
    public static final Font HEADING_FONT = new Font("Serif", Font.BOLD, 22);
    public static final Font HEADING_FONT_SM = new Font("Serif", Font.BOLD, 18);
    public static final Font AUTH_TITLE_FONT = new Font("SansSerif", Font.BOLD, 28);
    public static final Font AUTH_SUBTITLE_FONT = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font BODY_FONT_BOLD = new Font("SansSerif", Font.BOLD, 14);
    public static final Font FIELD_LABEL_FONT = new Font("SansSerif", Font.BOLD, 14);
    public static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font BRAND_FONT = new Font("SansSerif", Font.BOLD, 16);
    public static final Font NAV_FONT = new Font("SansSerif", Font.PLAIN, 16);
    public static final Font NAV_FONT_ACTIVE = new Font("SansSerif", Font.BOLD, 16);
    public static final Font SIDEBAR_NAME_FONT = new Font("SansSerif", Font.BOLD, 16);
}
