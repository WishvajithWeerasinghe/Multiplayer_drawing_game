package com.example.drawclient;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        String server = args.length > 0 ? args[0] : "http://localhost:8081";
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LobbyFrame(new ApiClient(server)).setVisible(true));
    }
}
