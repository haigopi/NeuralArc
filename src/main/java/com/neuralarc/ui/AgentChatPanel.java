package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Supplier;

/**
 * The analyst as a conversation, in a tab beside the event log.
 *
 * <p>The Ask the Analyst dialog answers one question and forgets it. Most real questions are follow-ups
 * — "why is NVDA down?", "what would cancelling those do?" — and asking each one from scratch pays to
 * re-read the same positions every time. This keeps one conversation: the model still has the last
 * answer, and one tool-call budget covers the whole exchange rather than resetting per question.
 *
 * <p>Layout and validation only. Each message goes to {@link AgentAnalystRunner.Conversation} on a
 * {@link SwingWorker}, because it blocks on the model and on broker reads for as long as the answer
 * takes; while it runs the input is disabled and the status line says what is happening, so it is
 * visible that money is being spent.
 */
final class AgentChatPanel extends JPanel {
    static final String PLACEHOLDER = "Ask about your positions, prices or what to do next…";
    private static final Color TEXT = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color OPERATOR = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    private static final Color WARNING = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(AgentChatPanel.class.getName());

    private final Supplier<AgentAnalystRunner> runners;
    private final java.util.function.Consumer<String> commands;
    private final AnalystAnswerCache answers;
    private final JTextPane transcript = new JTextPane();
    private final JTextField input = new JTextField();
    private final JButton ask = new JButton("Ask");
    private final JButton reset = new JButton("New");
    private final JButton expand = new JButton();
    private final JProgressBar working = new JProgressBar();
    private final JLabel status = new JLabel(" ");
    private AgentAnalystRunner.Conversation conversation;

    AgentChatPanel(Supplier<AgentAnalystRunner> runners) {
        this(runners, null, new AnalystAnswerCache());
    }

    AgentChatPanel(Supplier<AgentAnalystRunner> runners, java.util.function.Consumer<String> commands,
                   AnalystAnswerCache answers) {
        super(new BorderLayout(0, 6));
        this.runners = runners;
        this.commands = commands;
        this.answers = answers == null ? new AnalystAnswerCache() : answers;
        setOpaque(false);
        setBorder(new EmptyBorder(2, 2, 2, 2));

        transcript.setEditable(false);
        transcript.setFont(FontLoader.ui(Font.PLAIN, 11.5f));
        transcript.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(transcript);
        scroll.setBorder(BorderFactory.createLineBorder(
                ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200))));
        add(scroll, BorderLayout.CENTER);

        input.putClientProperty("JTextField.placeholderText", PLACEHOLDER);
        input.setFont(FontLoader.ui(Font.PLAIN, 11.5f));
        input.addActionListener(event -> send());
        DialogButtonStyles.apply(ask, "icons/send.svg");
        ask.addActionListener(event -> send());
        DialogButtonStyles.apply(reset, "icons/refresh.svg");
        reset.setToolTipText(TooltipStyler.text("Start a fresh conversation: the analyst forgets what was said and"
                + " the tool-call budget starts again.", 320));
        reset.addActionListener(event -> startOver());

        DialogButtonStyles.apply(expand, "icons/chart.svg");
        expand.setToolTipText(TooltipStyler.text("Open the latest answer full screen, where a long analysis can"
                + " actually be read.", 320));
        expand.addActionListener(event -> openLatestFullScreen());
        JPanel buttons = new JPanel(new BorderLayout(6, 0));
        buttons.setOpaque(false);
        buttons.add(ask, BorderLayout.CENTER);
        JPanel trailing = new JPanel(new BorderLayout(6, 0));
        trailing.setOpaque(false);
        trailing.add(expand, BorderLayout.CENTER);
        trailing.add(reset, BorderLayout.EAST);
        buttons.add(trailing, BorderLayout.EAST);
        JPanel entry = new JPanel(new BorderLayout(6, 0));
        entry.setOpaque(false);
        entry.add(input, BorderLayout.CENTER);
        entry.add(buttons, BorderLayout.EAST);

        status.setFont(FontLoader.ui(Font.PLAIN, 10f));
        status.setForeground(MUTED);
        working.setIndeterminate(true);
        working.setVisible(false);
        working.setPreferredSize(new java.awt.Dimension(0, 3));
        JPanel footer = new JPanel(new BorderLayout(0, 2));
        footer.setOpaque(false);
        footer.add(working, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        JPanel south = new JPanel(new BorderLayout(0, 3));
        south.setOpaque(false);
        south.add(entry, BorderLayout.NORTH);
        south.add(footer, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        append("The analyst reads your positions, live prices, NeuralArc's own levels, recent headlines and what the"
                + " Portfolio Actions menu could do. It cannot place, change or cancel any order — everything it says"
                + " is decision support you act on yourself."
                + "\nStart a line with / to run an action instead of asking, e.g. /cancel staged.", MUTED, Font.PLAIN);
        setStatus(" ", false);
    }

    JTextField inputField() {
        return input;
    }

    JButton askButton() {
        return ask;
    }

    String statusText() {
        return status.getText();
    }

    String transcriptText() {
        return transcript.getText();
    }

    /** Drops the conversation so the next question starts fresh, with a new budget. */
    void startOver() {
        conversation = null;
        transcript.setText("");
        append("New conversation. The analyst has forgotten what was said before this line.", MUTED, Font.ITALIC);
        setStatus(" ", false);
    }

    /** Sends whatever is typed, unless a message is already in flight. */
    void send() {
        String asked = input.getText().trim();
        if (asked.isEmpty() || !ask.isEnabled()) {
            return;
        }
        // A leading slash means "run this", not "think about this": straight to the palette, no model,
        // no tokens, no waiting.
        if (asked.startsWith("/")) {
            if (commands == null) {
                setStatus("Commands are not available here.", true);
                return;
            }
            input.setText("");
            setStatus("Opened the action palette for \"" + CommandMatcher.clean(asked) + "\".", false);
            commands.accept(asked);
            return;
        }
        // The cache is checked before the analyst's own settings: repeating a question that has already
        // been answered needs no key, no connection and no call.
        java.util.Optional<AnalystAnswerCache.Answer> remembered = answers.find(asked);
        AgentAnalystRunner runner = runners == null ? null : runners.get();
        if (runner == null && remembered.isEmpty()) {
            setStatus("The AI analyst is off. Switch it on in Settings and add an Anthropic API key.", true);
            return;
        }
        append("You", OPERATOR, Font.BOLD);
        append(asked, TEXT, Font.PLAIN);
        input.setText("");
        if (remembered.isPresent()) {
            AnalystAnswerCache.Answer answer = remembered.get();
            LOGGER.info(() -> "[AGENT][CHAT][CACHE_HIT] Reusing the answer given "
                    + answer.ageText(java.time.Instant.now()) + " instead of calling the model.");
            append("Analyst", MUTED, Font.BOLD);
            append(answer.outcome().text(), TEXT, Font.PLAIN);
            setStatus("Answered " + answer.ageText(java.time.Instant.now())
                    + " — repeated from the last " + AnalystAnswerCache.TTL.toMinutes()
                    + " minutes, so nothing was billed. Press New to ask it fresh.", false);
            return;
        }
        if (conversation == null) {
            conversation = runner.newConversation();
        }
        ask.setEnabled(false);
        input.setEnabled(false);
        working.setVisible(true);
        setStatus("Thinking… reading prices, positions and news.", false);
        LOGGER.info(() -> "[AGENT][CHAT][ASK] " + summarize(asked));
        AgentAnalystRunner.Conversation active = conversation;
        new SwingWorker<AgentAnalystRunner.Outcome, Void>() {
            @Override
            protected AgentAnalystRunner.Outcome doInBackground() throws Exception {
                return active.ask(asked);
            }

            @Override
            protected void done() {
                ask.setEnabled(true);
                input.setEnabled(true);
                working.setVisible(false);
                input.requestFocusInWindow();
                try {
                    AgentAnalystRunner.Outcome outcome = get();
                    answers.remember(asked, outcome);
                    LOGGER.info(() -> "[AGENT][CHAT][ANSWERED] run=" + outcome.runId()
                            + " toolCalls=" + outcome.toolCalls() + " turns=" + outcome.turns()
                            + " completed=" + outcome.completed() + " chars=" + outcome.text().length());
                    show(outcome);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    append("Analyst", WARNING, Font.BOLD);
                    append(message, WARNING, Font.PLAIN);
                    setStatus("That question failed. Nothing was placed or changed.", true);
                    LOGGER.log(java.util.logging.Level.WARNING, "[AGENT][CHAT][FAILED] " + message, cause);
                    // A failed start leaves no usable conversation; the next question builds a new one.
                    if (active.messageCount() <= 1) {
                        conversation = null;
                    }
                }
            }
        }.execute();
    }

    private void show(AgentAnalystRunner.Outcome outcome) {
        append("Analyst", MUTED, Font.BOLD);
        append(outcome.text().isBlank() ? "(no answer)" : outcome.text(), TEXT, Font.PLAIN);
        if (outcome.refused()) {
            setStatus("The model declined that question.", true);
            return;
        }
        String spend = outcome.toolCalls() + " tool call" + (outcome.toolCalls() == 1 ? "" : "s")
                + " over " + outcome.turns() + " exchange" + (outcome.turns() == 1 ? "" : "s");
        int remaining = conversation == null ? 0 : conversation.callsRemaining();
        if (!outcome.completed()) {
            setStatus("Stopped at its limit after " + spend + " — this answer may be unfinished. Raise the limits in"
                    + " Settings, ask something narrower, or press New to start over.", true);
            return;
        }
        setStatus(spend + " · " + remaining + " tool call" + (remaining == 1 ? "" : "s")
                + " left in this conversation.", false);
    }

    /** Opens the most recent answer full screen; with none yet, says so rather than opening blank. */
    void openLatestFullScreen() {
        java.util.Optional<AnalystAnswerCache.Answer> latest = answers.mostRecent();
        if (latest.isEmpty()) {
            setStatus("Nothing to open yet — ask the analyst something first.", true);
            return;
        }
        LOGGER.info("[AGENT][CHAT][EXPAND] Opening the latest answer full screen.");
        new AnalystAnswerDialog(this, latest.get(), java.time.Instant.now()).setVisible(true);
    }

    JButton expandButton() {
        return expand;
    }

    boolean isWorking() {
        return working.isVisible();
    }

    /** A question, trimmed for the log: enough to identify it, not enough to fill the file. */
    private static String summarize(String question) {
        return question.length() <= 120 ? question : question.substring(0, 117) + "…";
    }

    private void append(String text, Color color, int style) {
        StyledDocument document = transcript.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        StyleConstants.setBold(attributes, style == Font.BOLD);
        StyleConstants.setItalic(attributes, style == Font.ITALIC);
        try {
            document.insertString(document.getLength(), text + "\n", attributes);
        } catch (BadLocationException ignored) {
            // The document only ever grows at its end; nothing to recover from.
        }
        transcript.setCaretPosition(document.getLength());
    }

    private void setStatus(String text, boolean warning) {
        status.setText(text);
        status.setForeground(warning ? WARNING : MUTED);
    }

    static Color transcriptBackground() {
        Color background = UIManager.getColor("TextPane.background");
        return background == null ? Color.DARK_GRAY : background;
    }
}
