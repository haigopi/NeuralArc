package com.neuralarc.ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

public final class DialogCloseActions {
    private static final String ESCAPE_CLOSE_ACTION_KEY = "dialog.closeOnEscape";

    private DialogCloseActions() {
    }

    public static void bindEscapeToClose(JDialog dialog) {
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ESCAPE_CLOSE_ACTION_KEY);
        dialog.getRootPane().getActionMap().put(ESCAPE_CLOSE_ACTION_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
            }
        });
    }
}
