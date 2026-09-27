package com.neuralarc.ui;

import com.neuralarc.agent.tools.PortfolioActionCatalog;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Type a few letters, press Enter, run a Portfolio Action.
 *
 * <p>The analyst is for judgement; this is for speed. Reaching an action through the menu means eight
 * submenus, and asking a model to run one costs seconds, tokens and a chance of picking the wrong
 * neighbour — while an action here always resolves to the same command for the same letters, with no
 * API call at all.
 *
 * <p>It runs the action exactly as the menu does, which means the action's own confirmation still
 * appears: this palette is a faster way to reach a command, never a way to skip what it asks.
 */
final class CommandPalette extends JDialog {
    static final String HINT = "Type to find an action · Enter runs it · Esc closes";
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color WARNING = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));

    private final List<CommandMatcher.Command> commands;
    private final Map<String, Integer> matchCounts;
    private final JTextField query = new JTextField();
    private final DefaultListModel<CommandMatcher.Command> model = new DefaultListModel<>();
    private final JList<CommandMatcher.Command> results = new JList<>(model);
    private final JLabel hint = new JLabel(HINT);

    CommandPalette(Component parent, List<CommandMatcher.Command> commands, PortfolioActionCatalog catalog,
                   String initialQuery) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), "Run an Action",
                ModalityType.APPLICATION_MODAL);
        DialogCloseActions.bindEscapeToClose(this);
        this.commands = commands == null ? List.of() : List.copyOf(commands);
        this.matchCounts = countsFrom(catalog);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 14, 10, 14));
        query.setFont(FontLoader.ui(Font.PLAIN, 13f));
        query.putClientProperty("JTextField.placeholderText", "cancel staged…");
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh(); }
            @Override public void removeUpdate(DocumentEvent event) { refresh(); }
            @Override public void changedUpdate(DocumentEvent event) { refresh(); }
        });
        query.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> moveSelection(1);
                    case KeyEvent.VK_UP -> moveSelection(-1);
                    case KeyEvent.VK_ENTER -> runSelected();
                    default -> {
                    }
                }
            }
        });
        content.add(query, BorderLayout.NORTH);

        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setCellRenderer(new CommandRenderer());
        results.setFont(FontLoader.ui(Font.PLAIN, 12f));
        results.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    runSelected();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(results);
        scroll.setBorder(BorderFactory.createLineBorder(
                ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200))));
        scroll.setPreferredSize(new Dimension(520, 320));
        content.add(scroll, BorderLayout.CENTER);

        hint.setFont(FontLoader.ui(Font.PLAIN, 10f));
        hint.setForeground(MUTED);
        content.add(hint, BorderLayout.SOUTH);
        setContentPane(content);

        query.setText(CommandMatcher.clean(initialQuery));
        refresh();
        pack();
        setLocationRelativeTo(parent);
    }

    JTextField queryField() {
        return query;
    }

    /** The commands currently listed, best match first. */
    List<CommandMatcher.Command> shown() {
        List<CommandMatcher.Command> listed = new ArrayList<>();
        for (int index = 0; index < model.size(); index++) {
            listed.add(model.get(index));
        }
        return listed;
    }

    CommandMatcher.Command selected() {
        return results.getSelectedValue();
    }

    String hintText() {
        return hint.getText();
    }

    /**
     * Runs the highlighted command and closes. A disabled one is refused with the menu's own reason
     * rather than silently doing nothing.
     */
    void runSelected() {
        CommandMatcher.Command command = selected();
        if (command == null) {
            return;
        }
        if (!command.enabled()) {
            hint.setText(command.description().isBlank() ? "That action is unavailable right now."
                    : "Unavailable: " + command.description());
            hint.setForeground(WARNING);
            return;
        }
        dispose();
        // After the palette closes, so the action's own confirmation owns the screen.
        SwingUtilities.invokeLater(command.action());
    }

    private void refresh() {
        model.clear();
        for (CommandMatcher.Command command : CommandMatcher.match(commands, query.getText())) {
            model.addElement(command);
        }
        if (!model.isEmpty()) {
            results.setSelectedIndex(0);
            results.ensureIndexIsVisible(0);
        }
        hint.setForeground(MUTED);
        hint.setText(model.isEmpty() ? "No action matches that." : HINT);
    }

    private void moveSelection(int delta) {
        if (model.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(model.size() - 1, results.getSelectedIndex() + delta));
        results.setSelectedIndex(next);
        results.ensureIndexIsVisible(next);
    }

    /** How many rows each action would touch, so the palette shows the same counts the analyst reads. */
    private static Map<String, Integer> countsFrom(PortfolioActionCatalog catalog) {
        if (catalog == null) {
            return Map.of();
        }
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (PortfolioActionCatalog.ActionPreview preview : catalog.previewAll()) {
            counts.put(preview.name(), preview.matchCount());
        }
        return counts;
    }

    /** Label, then the group and what it would act on, in the muted secondary style used elsewhere. */
    private final class CommandRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CommandMatcher.Command command) {
                setText(PortfolioActionsMenu.itemHtml(command.label(), secondaryLine(command)));
                setEnabled(command.enabled());
            }
            setBorder(new EmptyBorder(3, 6, 3, 6));
            return this;
        }

        private String secondaryLine(CommandMatcher.Command command) {
            Integer count = matchCounts.get(command.label());
            String scope = count == null ? "" : " · " + count + (count == 1 ? " row" : " rows");
            return command.group() + scope;
        }
    }
}
