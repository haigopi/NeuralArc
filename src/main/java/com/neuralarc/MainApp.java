package com.neuralarc;

import com.neuralarc.ui.TradingFrame;

import javax.swing.*;

public class MainApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TradingFrame().setVisible(true));
    }
}
