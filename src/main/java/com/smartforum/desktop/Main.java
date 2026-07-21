package com.smartforum.desktop;

import com.formdev.flatlaf.FlatLightLaf;
import com.smartforum.desktop.ui.AppWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("Could not set FlatLaf look and feel, falling back to default: " + e.getMessage());
        }

        AppContext ctx = new AppContext();

        SwingUtilities.invokeLater(() -> new AppWindow(ctx).setVisible(true));
    }
}
