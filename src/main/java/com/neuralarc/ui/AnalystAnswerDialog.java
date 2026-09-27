package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;

/**
 * One analyst answer, full screen and readable.
 *
 * <p>The chat transcript is a narrow column at the bottom of a trading window — fine for a sentence,
 * poor for the several paragraphs a real answer runs to. This opens the latest answer at screen size,
 * with the question above it and the line saying what the run cost, so it can be read properly and
 * copied somewhere else.
 */
final class AnalystAnswerDialog extends JDialog {
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color TEXT = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));

    private final JTextArea answer = new JTextArea();

    AnalystAnswerDialog(Component parent, AnalystAnswerCache.Answer remembered, Instant now) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "AI Analyst", ModalityType.MODELESS);
        DialogCloseActions.bindEscapeToClose(this);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(new EmptyBorder(14, 16, 12, 16));

        JLabel question = new JLabel(CompactFormLayout.wrapped(
                remembered == null ? "No answer yet" : remembered.question(), 900));
        question.setFont(FontLoader.ui(Font.BOLD, 14f));
        JLabel meta = new JLabel(metaText(remembered, now));
        meta.setFont(FontLoader.ui(Font.PLAIN, 10.5f));
        meta.setForeground(MUTED);
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(question, BorderLayout.NORTH);
        heading.add(meta, BorderLayout.SOUTH);
        content.add(heading, BorderLayout.NORTH);

        answer.setEditable(false);
        answer.setLineWrap(true);
        answer.setWrapStyleWord(true);
        answer.setFont(FontLoader.ui(Font.PLAIN, 13.5f));
        answer.setForeground(TEXT);
        answer.setBorder(new EmptyBorder(10, 12, 10, 12));
        answer.setText(remembered == null
                ? "Ask the analyst something first; the answer opens here."
                : remembered.outcome().text());
        answer.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(answer);
        scroll.setBorder(BorderFactory.createLineBorder(
                ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200))));
        content.add(scroll, BorderLayout.CENTER);

        JButton copy = new JButton("Copy");
        DialogButtonStyles.apply(copy, "icons/apply.svg");
        copy.addActionListener(event -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(answer.getText()), null));
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(copy);
        buttons.add(close);
        JLabel disclaimer = new JLabel("Decision support only — the analyst places nothing.");
        disclaimer.setFont(FontLoader.ui(Font.PLAIN, 10.5f));
        disclaimer.setForeground(MUTED);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(disclaimer, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        fillScreen(parent);
    }

    JTextArea answerArea() {
        return answer;
    }

    /** Opens at the size of the screen the app is on, leaving a small margin so it still reads as a window. */
    private void fillScreen(Component parent) {
        Rectangle screen = parent != null && parent.getGraphicsConfiguration() != null
                ? parent.getGraphicsConfiguration().getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int margin = 40;
        setMinimumSize(new Dimension(640, 480));
        setSize(Math.max(640, screen.width - margin * 2), Math.max(480, screen.height - margin * 2));
        setLocationRelativeTo(parent);
    }

    private static String metaText(AnalystAnswerCache.Answer remembered, Instant now) {
        if (remembered == null) {
            return " ";
        }
        AgentAnalystRunner.Outcome outcome = remembered.outcome();
        String spend = outcome.toolCalls() + " tool call" + (outcome.toolCalls() == 1 ? "" : "s")
                + " over " + outcome.turns() + " exchange" + (outcome.turns() == 1 ? "" : "s");
        String unfinished = outcome.completed() ? "" : " · stopped at its limit, so this may be unfinished";
        return "Answered " + remembered.ageText(now == null ? Instant.now() : now) + " · " + spend + unfinished;
    }
}
