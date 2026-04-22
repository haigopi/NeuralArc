package com.neuralarc;

import com.neuralarc.ui.TradingFrame;

import javax.swing.*;

public class MainApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TradingFrame frame = new TradingFrame();
            frame.setVisible(true);
            frame.promptForRequiredSettings();
        });
    }
}
