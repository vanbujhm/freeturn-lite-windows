package com.freeturn.lite;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class FreeTurnLiteApp {
    private FreeTurnLiteApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default Swing look and feel is acceptable.
            }
            String initialUri = args.length > 0 ? args[0] : "";
            new FreeTurnLiteFrame(initialUri).setVisible(true);
        });
    }
}
