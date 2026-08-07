package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small non-modal toasts stacked in the bottom-right of the frame's layered pane. Used for
 * background events the operator should notice but must not be interrupted by — an automatic
 * safety correction, for example. Never blocks trading interaction.
 */
final class ToastNotifier {
    private static final int TOAST_WIDTH = 340;
    private static final int MARGIN = 16;
    private static final int GAP = 8;
    private static final int DEFAULT_DURATION_MILLIS = 6_000;
    private static final int MAX_VISIBLE = 4;

    private final JFrame frame;
    private final Deque<JPanel> visibleToasts = new ArrayDeque<>();

    ToastNotifier(JFrame frame) {
        this.frame = frame;
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutToasts();
            }
        });
    }

    void showWarning(String message) {
        show(message, new Color(122, 74, 12), new Color(255, 214, 122));
    }

    void showSuccess(String message) {
        show(message, new Color(20, 84, 40), new Color(170, 235, 190));
    }

    void showInfo(String message) {
        show(message, new Color(28, 62, 110), new Color(186, 216, 250));
    }

    void show(TradeEventToastFormatter.ToastMessage message) {
        if (message == null) {
            return;
        }
        switch (message.severity()) {
            case SUCCESS -> showSuccess(message.text());
            case WARNING -> showWarning(message.text());
            case INFO -> showInfo(message.text());
        }
    }

    private void show(String message, Color foreground, Color background) {
        if (message == null || message.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JLayeredPane layeredPane = frame.getLayeredPane();
            if (layeredPane == null) {
                return;
            }
            JPanel toast = buildToast(message, foreground, background);
            layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
            visibleToasts.addFirst(toast);
            while (visibleToasts.size() > MAX_VISIBLE) {
                dismiss(visibleToasts.removeLast());
            }
            layoutToasts();

            Timer dismissTimer = new Timer(DEFAULT_DURATION_MILLIS, ignored -> {
                visibleToasts.remove(toast);
                dismiss(toast);
                layoutToasts();
            });
            dismissTimer.setRepeats(false);
            dismissTimer.start();
        });
    }

    private JPanel buildToast(String message, Color foreground, Color background) {
        JLabel label = new JLabel("<html><div style='width:" + (TOAST_WIDTH - 40) + "px;'>" + escape(message) + "</div></html>");
        label.setForeground(foreground);
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));

        JPanel toast = new JPanel();
        toast.setLayout(new java.awt.BorderLayout());
        toast.setBackground(background);
        toast.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(foreground.brighter(), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));
        toast.add(label, java.awt.BorderLayout.CENTER);
        Dimension preferred = toast.getPreferredSize();
        toast.setSize(TOAST_WIDTH, Math.max(44, preferred.height));
        return toast;
    }

    private void dismiss(JPanel toast) {
        JLayeredPane layeredPane = frame.getLayeredPane();
        if (layeredPane != null) {
            layeredPane.remove(toast);
            layeredPane.repaint();
        }
    }

    private void layoutToasts() {
        JLayeredPane layeredPane = frame.getLayeredPane();
        if (layeredPane == null) {
            return;
        }
        int y = layeredPane.getHeight() - MARGIN;
        for (JPanel toast : visibleToasts) {
            y -= toast.getHeight();
            toast.setLocation(layeredPane.getWidth() - TOAST_WIDTH - MARGIN, Math.max(MARGIN, y));
            y -= GAP;
        }
        layeredPane.repaint();
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
