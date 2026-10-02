package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;

/**
 * One window for every run that touches more than a handful of stocks.
 *
 * <p>Fifty re-entries is fifty broker round trips, and until now the app said nothing while they went
 * in: the window simply sat there, which is indistinguishable from a hang. This shows the count, the
 * stock being worked on, and each result as it lands.
 *
 * <p>It closes itself when the run finishes cleanly — a summary nobody needs to read is not worth a
 * click. It stays open when something failed, because an order that did not go in is the one thing
 * here that an operator must actually see.
 *
 * <p>Modeless on purpose: the grid stays usable, and "Hide" lets the run finish out of sight. There is
 * no cancel, because a batch stopped halfway leaves some orders placed and some not, and that is a
 * worse state than letting it finish.
 */
final class BulkProgressDialog extends JDialog implements BulkProgressHandle {
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color FAILED = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Color SKIPPED = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));

    private final BulkProgress progress;
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel headline = new JLabel(" ");
    private final DefaultListModel<BulkProgress.Line> model = new DefaultListModel<>();
    private final JList<BulkProgress.Line> results = new JList<>(model);
    private final JButton close = new JButton("Hide");
    private boolean done;

    /**
     * Opens the window for a run of {@code total} items, or hands back a silent handle where there is
     * no screen to put it on.
     */
    static BulkProgressHandle open(Component parent, String title, int total) {
        if (GraphicsEnvironment.isHeadless() || total <= 1) {
            // One item finishes before a window could usefully be read.
            return BulkProgressHandle.NONE;
        }
        BulkProgressDialog dialog = new BulkProgressDialog(parent, title, total);
        dialog.setVisible(true);
        return dialog;
    }

    /** Package-private so a test can drive the window without putting one on the screen. */
    BulkProgressDialog(Component parent, String title, int total) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title, ModalityType.MODELESS);
        this.progress = new BulkProgress(title, total);
        DialogCloseActions.bindEscapeToClose(this);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(new EmptyBorder(14, 16, 12, 16));

        headline.setFont(FontLoader.ui(Font.PLAIN, 12.5f));
        headline.setText(progress.headline("starting…"));
        bar.setStringPainted(true);
        bar.setString("0 of " + total);
        bar.setValue(0);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(headline, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        content.add(top, BorderLayout.NORTH);

        results.setCellRenderer(new OutcomeRenderer());
        results.setFont(FontLoader.ui(Font.PLAIN, 11.5f));
        JScrollPane scroll = new JScrollPane(results);
        scroll.setBorder(BorderFactory.createLineBorder(
                ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200))));
        scroll.setPreferredSize(new Dimension(460, 220));
        content.add(scroll, BorderLayout.CENTER);

        DialogButtonStyles.apply(close, "icons/close.svg");
        close.setToolTipText(TooltipStyler.text(
                "The run carries on in the background; its results are in the event log.", 300));
        close.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(parent);
    }

    @Override
    public void advance(String item, BulkProgress.Outcome outcome) {
        onEdt(() -> {
            progress.record(item, outcome);
            model.addElement(new BulkProgress.Line(item == null ? "" : item,
                    outcome == null ? BulkProgress.Outcome.DONE : outcome));
            results.ensureIndexIsVisible(model.size() - 1);
            headline.setText(progress.headline(shortName(item)));
            bar.setValue(progress.percent());
            bar.setString(progress.completed() + " of " + progress.total());
        });
    }

    @Override
    public void finish(List<String> successes, List<String> skipped, List<String> failures) {
        onEdt(() -> {
            progress.settle(successes, skipped, failures);
            done = true;
            bar.setValue(100);
            bar.setString(progress.completed() + " of " + progress.total());
            headline.setText(progress.summary());
            if (progress.clean()) {
                dispose();
                return;
            }
            // Only what went wrong: the successes are in the log, and burying three failures in fifty
            // lines is the same as not showing them.
            model.clear();
            progress.failures().forEach(model::addElement);
            close.setText("Close");
            close.setToolTipText(null);
            setTitle(progress.title() + " — " + progress.summary());
        });
    }

    /** Test seam: what the operator is being told right now. */
    String headlineText() {
        return headline.getText();
    }

    String progressText() {
        return bar.getString();
    }

    List<BulkProgress.Line> listedLines() {
        List<BulkProgress.Line> listed = new java.util.ArrayList<>();
        for (int index = 0; index < model.size(); index++) {
            listed.add(model.get(index));
        }
        return listed;
    }

    boolean finished() {
        return done;
    }

    String closeButtonText() {
        return close.getText();
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    /** "AAPL: placed at $12.40" is the line; "AAPL" is what belongs in the heading. */
    private static String shortName(String item) {
        if (item == null) {
            return "";
        }
        int colon = item.indexOf(':');
        return colon > 0 ? item.substring(0, colon) : item;
    }

    private static final class OutcomeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof BulkProgress.Line line) {
                setText(line.text());
                if (!isSelected) {
                    setForeground(switch (line.outcome()) {
                        case FAILED -> FAILED;
                        case SKIPPED -> MUTED;
                        case DONE -> SKIPPED;
                    });
                }
            }
            setBorder(new EmptyBorder(2, 6, 2, 6));
            return this;
        }
    }
}
