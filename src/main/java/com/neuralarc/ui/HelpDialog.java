package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;

public class HelpDialog extends JDialog {
    private static final Color TAB_CONTENT_BORDER = new Color(200, 206, 214);

    private static final Font HEADING_FONT = FontLoader.ui(Font.BOLD, 13);
    private static final Font BODY_FONT    = FontLoader.ui(Font.PLAIN, 13);

    private static final String[][] STRATEGY_FAQS = {
        {
            "📋  Strategy Dialog — What does each section mean?",
            "Strategy Parameters:\n" +
            "• Symbol is the stock ticker.\n" +
            "• Base buy price and quantity define the first limit buy.\n" +
            "• Trading mode defaults to Paper.\n\n" +
            "Risk Controls:\n" +
            "• Stop Loss is optional and can be enabled/disabled with a checkbox.\n" +
            "• When enabled, it acts as downside-exit protection after a position exists.\n" +
            "• Sell trigger price is the level that starts profit-taking logic.\n" +
            "• Loss Buy Level 1 and 2 are staged average-down buy levels.\n\n" +
            "Profit Hold Option:\n" +
            "• Enable Profit Hold keeps the strategy from selling immediately at the target.\n" +
            "• Percent Trailing exits after a percentage pullback from the highest observed price.\n" +
            "• Fixed Amount Trailing exits after a fixed dollar pullback from the highest observed price.\n\n" +
            "Execution:\n" +
            "• Polling interval seconds controls how often that strategy is evaluated.\n" +
            "• Cycle behavior (optional): Repeat cycle after profitable exit can be enabled to auto-start the same strategy again after a full profitable exit."
        },
        {
            "📐  Strategy Flow — How does one strategy run?",
            "NeuralArc runs one staged state machine per strategy.\n\n" +
            "1. Base buy places the first limit buy.\n" +
            "2. Buy Limit 1 can be placed only after the base buy is fully filled.\n" +
            "3. Buy Limit 2 can be placed only after Buy Limit 1 is fully filled.\n" +
            "4. Stop loss protection is evaluated after a position exists, but only when Stop Loss is enabled.\n" +
            "5. When the sell trigger is reached, the strategy either sells or enters profit hold.\n" +
            "6. Profit hold tracks the highest observed price and exits on the configured pullback.\n" +
            "7. If Repeat cycle after profitable exit is enabled, the next cycle starts only after a fully exited profitable sell (no remaining position).\n\n" +
            "Important: partial fills do not advance the strategy to the next buy stage and do not re-arm a new cycle."
        },
        {
            "🛑  Stop Loss — What does it mean here?",
            "Stop Loss in NeuralArc is downside protection after a position exists.\n\n" +
            "Stop Loss can be disabled from the Strategy dialog. When disabled, no stop-loss rule is evaluated and no stop-loss sell order is submitted.\n\n" +
            "It does not wait for the stock to go up first. Instead, it watches for price to fall to or below your configured stop threshold and then submits a sell order.\n\n" +
            "When stop loss executes, the strategy does not auto-repeat even if repeat-cycle is enabled.\n\n" +
            "It can be configured as:\n" +
            "• a fixed stop price, or\n" +
            "• a percent below average entry cost.\n\n" +
            "Example:\n" +
            "If your average cost is 10.00 and your stop loss is 9.20, the strategy exits if price falls to 9.20 or below."
        },
        {
            "🔐  Protective Floor to Lock Profit — How does that work?",
            "In NeuralArc, the profit-locking protective floor is handled by Profit Hold, not by Stop Loss.\n\n" +
            "After the stock reaches the target sell price, Profit Hold can keep the trade open and track the highest observed price. It then exits when price pulls back by your configured amount.\n\n" +
            "It can be configured as:\n" +
            "• Percent Trailing, or\n" +
            "• Fixed Amount Trailing.\n\n" +
            "Example:\n" +
            "If target is reached and the stock climbs to 12.00, a 5% trailing setting creates a moving protective floor at 11.40. If price falls to 11.40 or below, the strategy exits."
        },
        {
            "⏱️  Polling Interval — What does it control?",
            "Polling interval is per strategy, not global.\n\n" +
            "• It controls how often that strategy checks orders, position state, latest price, and exit conditions.\n" +
            "• A strategy set to 40 seconds should be evaluated around every 40 seconds.\n" +
            "• Paused strategies should not continue polling the broker for strategy evaluation.\n\n" +
            "Use longer intervals to reduce API traffic. Use shorter intervals only when you need tighter reaction time."
        },
        {
            "📄  Paper Trading Mode — What should I expect?",
            "Paper mode is the default and should be your normal starting point.\n\n" +
            "• It uses Alpaca Paper credentials, not Live credentials.\n" +
            "• It lets you validate buy levels, staged buys, stop loss, sell trigger, and profit hold behavior.\n" +
            "• It helps you measure request volume and timing safely before considering Live mode.\n\n" +
            "Recommendation: prove the workflow in Paper first, then review order behavior and logs before any live use."
        },
        {
            "📈  Live Trading Mode — What changes and what risks exist?",
            "Live mode uses real broker credentials and real money.\n\n" +
            "• Losses are real.\n" +
            "• Fills and timing can differ from paper behavior.\n" +
            "• Start with small size.\n" +
            "• Confirm that live trading is explicitly enabled in configuration before using it.\n\n" +
            "Live mode should be treated as opt-in and high risk."
        }
    };

    private static final String[][] APPLICATION_FAQS = {
        {
            "⚙️  Settings — What goes in each field?",
            "User Email: Used to identify your local app profile and derive the encryption key for saved settings.\n\n" +
            "Broker: Select ALPACA to connect your broker credentials.\n\n" +
            "Application Mode: Use Paper to test with broker paper credentials, or Live for real trading credentials.\n\n" +
            "API Key / API Secret: Broker credentials. They are encrypted before being saved locally.\n\n" +
            "Telemetry: Operational events only. No personal secrets or API credentials are sent."
        },
        {
            "💤  Mac Sleep — Will the app keep trading?",
            "If your Mac goes to sleep, the app can remain open, but it does not keep actively running strategy logic while the machine is asleep.\n\n" +
            "What stops during sleep:\n" +
            "• Java timers and polling\n" +
            "• live network activity such as trade update streams\n\n" +
            "What happens after wake:\n" +
            "• the app resumes\n" +
            "• polling starts again\n" +
            "• the app reconnects and reconciles strategy state with Alpaca\n\n" +
            "If you want continuous strategy execution, keep the Mac awake."
        },
        {
            "🦙  Alpaca — What endpoints does the app use?",
            "The app reads Alpaca endpoints from app.properties.\n\n" +
            "• Paper Trading REST: https://paper-api.alpaca.markets\n" +
            "• Live Trading REST: https://api.alpaca.markets\n" +
            "• Market Data: https://data.alpaca.markets\n\n" +
            "To connect:\n" +
            "1. Generate Alpaca Paper or Live credentials.\n" +
            "2. Paste them into Settings.\n" +
            "3. Verify the connection.\n" +
            "4. Keep Paper as the default unless you intentionally enable Live."
        },
        {
            "🛡️  Strategy Persistence — Where is strategy state saved?",
            "Strategies, orders, and execution events are stored locally under ~/.neuralarc/.\n\n" +
            "This allows the app to restore strategies on startup and reconcile them with Alpaca.\n\n" +
            "Paused strategies remain paused. Active strategies can continue from the saved local state after restart."
        },
        {
            "🔒  Privacy & Telemetry",
            "Telemetry is for operational insight such as app events, strategy state changes, and reliability signals.\n\n" +
            "What is not sent:\n" +
            "• API keys\n" +
            "• API secrets\n" +
            "• local credential files\n\n" +
            "If you want the safest workflow, use Alpaca Paper mode while telemetry is disabled."
        },
        {
            "⚠️  KILL SWITCH — Emergency Stop for All Strategies",
            "The KILL SWITCH immediately stops all running strategies.\n\n" +
            "What it does:\n" +
            "• stops polling and strategy execution\n" +
            "• marks running strategies as paused\n" +
            "• saves the updated strategy state\n\n" +
            "What it does not do:\n" +
            "• it does not close the app\n" +
            "• it does not automatically liquidate positions\n\n" +
            "Use it when strategy activity must stop immediately and you want to review the situation first."
        }
    };

    public HelpDialog(JFrame owner) {
        super(owner, "Help & FAQ", true);
        setLayout(new BorderLayout(0, 0));
        setMinimumSize(new Dimension(760, 600));

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        tabs.setUI(new StandardTabbedPaneUI());
        tabs.setBorder(null);
        tabs.setFont(FontLoader.ui(Font.PLAIN, 12f));
        tabs.addTab("Strategy", buildFaqScrollPane(STRATEGY_FAQS));
        tabs.addTab("Application", buildFaqScrollPane(APPLICATION_FAQS));
        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBorder(new EmptyBorder(12, 24, 16, 24));
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(e -> setVisible(false));
        footer.add(close);
        add(footer, BorderLayout.SOUTH);

        pack();
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = Math.min(getWidth(), (int)(screen.width * 0.75));
        int maxHeight = Math.min(getHeight(), (int)(screen.height * 0.85));
        setSize(Math.max(760, maxWidth), Math.max(600, maxHeight));
        setLocationRelativeTo(owner);
    }

    private JPanel buildFaqPanel(String title, String body) {
        JPanel panel = new JPanel(new BorderLayout());
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(HEADING_FONT);
        border.setTitleColor(new Color(40, 40, 120));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border,
                new EmptyBorder(12, 14, 14, 14)
        ));

        JTextArea text = new JTextArea(body);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(BODY_FONT);
        text.setForeground(new Color(50, 50, 50));
        text.setBorder(new EmptyBorder(0, 2, 0, 2));

        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane buildFaqScrollPane(String[][] faqs) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        for (String[] faq : faqs) {
            content.add(buildFaqPanel(faq[0], faq[1]));
            content.add(Box.createVerticalStrut(14));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    private static final class StandardTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(5, 12, 5, 12);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(4, 4, 0, 4);
            contentBorderInsets = new Insets(1, 1, 1, 1);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            g.setColor(tabPane.getBackgroundAt(tabIndex));
            g.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            g.setColor(TAB_CONTENT_BORDER.darker());
            g.drawLine(x, y + h - 1, x, y);
            g.drawLine(x, y, x + w - 1, y);
            g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
            if (!isSelected) {
                g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
            }
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return Math.max(super.calculateTabHeight(tabPlacement, tabIndex, fontHeight), 26);
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 6;
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            g.setColor(TAB_CONTENT_BORDER);
            int top = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) - 1;
            g.drawRect(4, top, tabPane.getWidth() - 9, tabPane.getHeight() - top - 5);
        }
    }
}
