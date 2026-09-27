package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JProgressBar;
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

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(AgentAnalystDialog.class.getName());

    private final AgentAnalystRunner runner;
    private final AnalystAnswerCache answers;
    private final JProgressBar working = new JProgressBar();
    private final JButton expand = new JButton("Full Screen");
    private final AgentAnalystHistory history = new AgentAnalystHistory();
    private final JTextField question = new JTextField(DEFAULT_QUESTION, 48);
    private final JTextArea answer = new JTextArea(16, 60);
    private final JLabel status = new JLabel(" ");
    private final JButton ask = new JButton("Ask the Analyst");
    private final JPanel historyList = new JPanel();
    private final CollapsibleSectionPanel historySection;

    AgentAnalystDialog(Frame owner, AgentAnalystRunner runner) {
        this(owner, runner, new AnalystAnswerCache());
    }

    AgentAnalystDialog(Frame owner, AgentAnalystRunner runner, AnalystAnswerCache answers) {
        super(owner, "AI Analyst", false);
        this.runner = runner;
        this.answers = answers == null ? new AnalystAnswerCache() : answers;
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

        historyList.setOpaque(false);
        historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(BorderFactory.createEmptyBorder());
        historyScroll.setPreferredSize(new Dimension(600, 160));
        historySection = new CollapsibleSectionPanel("History", historyScroll);
        historySection.setCollapsed(true);
        refreshHistoryList();

        JPanel centerColumn = new JPanel(new BorderLayout(8, 8));
        centerColumn.setOpaque(false);
        centerColumn.add(answerScroll, BorderLayout.CENTER);
        centerColumn.add(historySection, BorderLayout.SOUTH);

        status.setFont(FontLoader.ui(Font.PLAIN, 10.5f));
        status.setForeground(TEXT_MUTED);
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(event -> dispose());
        DialogButtonStyles.apply(expand, "icons/chart.svg");
        expand.setToolTipText(TooltipStyler.text("Open this answer full screen, where a long analysis can actually"
                + " be read.", 320));
        expand.addActionListener(event -> openFullScreen());
        working.setIndeterminate(true);
        working.setVisible(false);
        working.setPreferredSize(new Dimension(0, 3));
        JPanel actions = new JPanel(new BorderLayout(0, 4));
        actions.setOpaque(false);
        actions.add(working, BorderLayout.NORTH);
        actions.add(status, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(expand);
        right.add(close);
        actions.add(right, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(centerColumn, BorderLayout.CENTER);
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

    /** Opens the answer on screen at full size; with nothing answered yet, says so. */
    void openFullScreen() {
        java.util.Optional<AnalystAnswerCache.Answer> latest = answers.find(question.getText().trim());
        AnalystAnswerCache.Answer shown = latest.orElseGet(() -> answers.mostRecent().orElse(null));
        if (shown == null) {
            setStatus("Nothing to open yet — ask the analyst something first.", true);
            return;
        }
        new AnalystAnswerDialog(this, shown, java.time.Instant.now()).setVisible(true);
    }

    JButton expandButton() {
        return expand;
    }

    boolean isWorking() {
        return working.isVisible();
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
        java.util.Optional<AnalystAnswerCache.Answer> remembered = answers.find(asked);
        if (remembered.isPresent()) {
            AnalystAnswerCache.Answer cached = remembered.get();
            LOGGER.info(() -> "[AGENT][DIALOG][CACHE_HIT] Reusing the answer given "
                    + cached.ageText(java.time.Instant.now()) + " instead of calling the model.");
            answer.setText(cached.outcome().text());
            answer.setForeground(UIManager.getColor("TextArea.foreground"));
            answer.setCaretPosition(0);
            setStatus("Answered " + cached.ageText(java.time.Instant.now()) + " — repeated from the last "
                    + AnalystAnswerCache.TTL.toMinutes() + " minutes, so nothing was billed.", false);
            return;
        }
        ask.setEnabled(false);
        answer.setText("");
        answer.setForeground(UIManager.getColor("TextArea.foreground"));
        working.setVisible(true);
        LOGGER.info(() -> "[AGENT][DIALOG][ASK] " + (asked.length() <= 120 ? asked : asked.substring(0, 117) + "…"));
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
                    AgentAnalystRunner.Outcome outcome = get();
                    show(outcome);
                    recordHistory(asked, outcome.text(), status.getText(), outcome.refused() || !outcome.completed());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    setStatus("The run failed: " + message, true);
                    LOGGER.log(java.util.logging.Level.WARNING, "[AGENT][DIALOG][FAILED] " + message, cause);
                    recordHistory(asked, "The run failed: " + message, "Failed", true);
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

    /** Records one AI Analyst question/answer into the closed History accordion, newest first. */
    void recordHistory(String askedQuestion, String answerText, String statusSummary, boolean warning) {
        history.record(askedQuestion, answerText, statusSummary, warning);
        refreshHistoryList();
    }

    int historyEntryCount() {
        return history.entries().size();
    }

    boolean historySectionCollapsed() {
        return historySection.isCollapsed();
    }

    private void refreshHistoryList() {
        historyList.removeAll();
        if (history.isEmpty()) {
            JLabel empty = new JLabel("No questions asked yet.");
            empty.setForeground(TEXT_MUTED);
            empty.setFont(FontLoader.ui(Font.PLAIN, 11f));
            empty.setBorder(new EmptyBorder(6, 6, 6, 6));
            historyList.add(empty);
        } else {
            for (AgentAnalystHistory.Entry entry : history.entries()) {
                historyList.add(buildHistoryEntry(entry));
            }
        }
        historyList.revalidate();
        historyList.repaint();
    }

    private JComponent buildHistoryEntry(AgentAnalystHistory.Entry entry) {
        JLabel meta = new JLabel(entry.timestamp() + " · " + entry.statusSummary());
        meta.setFont(FontLoader.ui(Font.PLAIN, 10f));
        meta.setForeground(TEXT_MUTED);
        meta.setBorder(new EmptyBorder(0, 4, 4, 4));

        JTextArea entryAnswer = new JTextArea(entry.answer());
        entryAnswer.setEditable(false);
        entryAnswer.setLineWrap(true);
        entryAnswer.setWrapStyleWord(true);
        entryAnswer.setOpaque(false);
        entryAnswer.setFont(FontLoader.ui(Font.PLAIN, 11.5f));
        entryAnswer.setForeground(entry.warning() ? WARNING : UIManager.getColor("TextArea.foreground"));
        entryAnswer.setBorder(new EmptyBorder(0, 4, 4, 4));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(meta, BorderLayout.NORTH);
        body.add(entryAnswer, BorderLayout.CENTER);

        CollapsibleSectionPanel item = new CollapsibleSectionPanel(truncate(entry.question(), 70), body);
        item.setCollapsed(true);
        return item;
    }

    private static String truncate(String text, int maxLength) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength - 1) + "…";
    }

    private void setStatus(String text, boolean warning) {
        status.setText(text);
        status.setForeground(warning ? WARNING : TEXT_MUTED);
    }
}
