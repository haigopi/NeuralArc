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
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;

/**
 * Ask the read-only AI analyst a question about the book and read its answer.
 *
 * <p>Layout and validation only: the run itself is handed to {@link AgentAnalystRunner} on a
 * {@link SwingWorker}, because it blocks on the model and on broker reads for as long as the answer
 * takes. While it runs the button is disabled and the status line says what is happening, so the
 * operator can see that money is being spent and roughly on what.
 */
final class AgentAnalystDialog extends JDialog {
    static final String DEFAULT_QUESTION = "What stands out in my open positions today, and what should I watch?";
    static final String HINT = "The analyst answers here. It reads market hours, your open positions, live prices,"
            + " NeuralArc's own Auto Analyze levels and recent headlines before it writes anything, so the first"
            + " answer takes a little while. Each run is billed to your Anthropic key.";
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color WARNING = ThemeColors.color("NeuralArc.pnlNegative", new Color(166, 45, 45));

    private final AgentAnalystRunner runner;
    private final JTextField question = new JTextField(DEFAULT_QUESTION, 48);
    private final JTextArea answer = new JTextArea(16, 60);
    private final JLabel status = new JLabel(" ");
    private final JButton ask = new JButton("Ask the Analyst");

    AgentAnalystDialog(Frame owner, AgentAnalystRunner runner) {
        super(owner, "AI Analyst", false);
        this.runner = runner;
        DialogCloseActions.bindEscapeToClose(this);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel disclaimer = new JLabel("<html><div style='width:520px;'>The analyst reads your positions and"
                + " the market through NeuralArc's own data. It cannot place, change or cancel any order."
                + " Everything it writes is decision support — review it yourself before you trade.</div></html>");
        disclaimer.setForeground(TEXT_MUTED);
        disclaimer.setFont(FontLoader.ui(Font.PLAIN, 10.5f));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);
        top.add(disclaimer, BorderLayout.NORTH);
        JPanel askRow = new JPanel(new BorderLayout(8, 0));
        askRow.setOpaque(false);
        askRow.add(new JLabel("Question:"), BorderLayout.WEST);
        askRow.add(question, BorderLayout.CENTER);
        DialogButtonStyles.apply(ask, "icons/actions.svg");
        askRow.add(ask, BorderLayout.EAST);
        top.add(askRow, BorderLayout.SOUTH);

        answer.setEditable(false);
        answer.setText(HINT);
        answer.setForeground(TEXT_MUTED);
        answer.setLineWrap(true);
        answer.setWrapStyleWord(true);
        answer.setFont(FontLoader.ui(Font.PLAIN, 12f));
        answer.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane answerScroll = new JScrollPane(answer);
        answerScroll.setBorder(BorderFactory.createLineBorder(
                ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200))));

        status.setFont(FontLoader.ui(Font.PLAIN, 10.5f));
        status.setForeground(TEXT_MUTED);
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(status, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(close);
        actions.add(right, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(answerScroll, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        ask.addActionListener(event -> start());
        question.addActionListener(event -> start());
        getRootPane().setDefaultButton(ask);
        setMinimumSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(owner);
    }

    JTextField questionField() {
        return question;
    }

    JButton askButton() {
        return ask;
    }

    String statusText() {
        return status.getText();
    }

    String answerText() {
        return answer.getText();
    }

    /** Starts a run unless one is already going or the question is empty. */
    void start() {
        String asked = question.getText().trim();
        if (asked.isEmpty()) {
            setStatus("Type a question first.", true);
            return;
        }
        if (!ask.isEnabled()) {
            return;
        }
        ask.setEnabled(false);
        answer.setText("");
        answer.setForeground(UIManager.getColor("TextArea.foreground"));
        setStatus("Thinking… the analyst is reading prices, positions and news.", false);
        new SwingWorker<AgentAnalystRunner.Outcome, Void>() {
            @Override
            protected AgentAnalystRunner.Outcome doInBackground() throws Exception {
                return runner.run(asked);
            }

            @Override
            protected void done() {
                ask.setEnabled(true);
                try {
                    show(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    setStatus("The run failed: " + message, true);
                }
            }
        }.execute();
    }

    private void show(AgentAnalystRunner.Outcome outcome) {
        answer.setText(outcome.text());
        answer.setCaretPosition(0);
        if (outcome.refused()) {
            setStatus("The model declined this question.", true);
            return;
        }
        String spend = outcome.toolCalls() + " tool call" + (outcome.toolCalls() == 1 ? "" : "s")
                + " over " + outcome.turns() + " exchange" + (outcome.turns() == 1 ? "" : "s");
        if (!outcome.completed()) {
            setStatus("Stopped at its limit after " + spend + ". This answer may be unfinished — "
                    + "raise the limits in Settings or ask something narrower.", true);
            return;
        }
        setStatus("Done: " + spend + ".", false);
    }

    private void setStatus(String text, boolean warning) {
        status.setText(text);
        status.setForeground(warning ? WARNING : TEXT_MUTED);
    }
}
