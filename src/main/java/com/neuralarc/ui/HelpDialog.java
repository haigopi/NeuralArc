package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

public class HelpDialog extends JDialog {

    private static final Font HEADING_FONT = FontLoader.ui(Font.BOLD, 13);
    private static final Font BODY_FONT    = FontLoader.ui(Font.PLAIN, 13);

    private static final String[][] FAQS = {
        {
            "⚙️  Settings — What goes in each field?",
            "User Email: Used to identify your local app profile and derive the encryption key for saved settings.\n\n" +
            "Broker: Select ALPACA to connect your broker credentials.\n\n" +
            "Application Mode: Use Paper to test with broker paper credentials, or Live for real trading credentials.\n\n" +
            "API Key / API Secret: Broker credentials. They are encrypted before being saved locally.\n\n" +
            "Telemetry: Operational events only. No personal secrets or API credentials are sent."
        },
        {
            "📄  Paper Trading Mode — What is it and when should I use it?",
            "Paper mode lets you test strategy behavior without risking real money.\n\n" +
            "• Orders use the app's mock execution path.\n" +
            "• P&L, positions, and rule triggers remain simulated.\n" +
            "• It is the right place to validate buy levels, stop-loss behavior, sell triggers, and recovery buys.\n\n" +
            "Recommendation: Prove the strategy in Paper first. Move to Live only after repeated stable runs."
        },
        {
            "📐  Strategy Rules — How are the thresholds interpreted?",
            "NeuralArc uses direct threshold checks:\n\n" +
            "• Base buy price: buys when price is less than or equal to the configured level.\n" +
            "• Loss Buy Level 1 / 2: adds shares when price falls to or below those levels.\n" +
            "• Stop Loss: activates downside protection after the configured level is reached.\n" +
            "• Sell trigger price: profit-taking starts from this level.\n" +
            "• Profit Hold: delays selling if price continues at least 10% higher than the sell trigger."
        },
        {
            "📈  Live Trading Mode — What changes and what risks exist?",
            "Live mode uses real broker credentials and real money.\n\n" +
            "• Losses are real.\n" +
            "• Market fills may differ from mock behavior because of liquidity and execution timing.\n" +
            "• Start with small size.\n" +
            "• Confirm your broker keys allow trading before enabling live strategies.\n\n" +
            "Current limitation: the app works with whole-share long positions only."
        },
        {
            "🦙  Alpaca — What is it and how do I connect?",
            "Alpaca is the broker integration option for Paper and Live account modes.\n\n" +
            "Quick setup:\n" +
            "1. Create an Alpaca account.\n" +
            "2. Generate Paper or Live API credentials in the Alpaca dashboard.\n" +
            "3. Paste them into Settings → Alpaca API Details.\n" +
            "4. Select ALPACA and click Verify Connection.\n\n" +
            "Important: In the current app version, the Alpaca execution path is still limited. Use Paper mode for safe validation."
        },
        {
            "🛡️  Strategy Persistence — Where are my strategies saved?",
            "Strategies are saved immediately to:\n" +
            "   ~/.neuralarc/strategies.dat\n\n" +
            "The file is encrypted using a key derived from your user email.\n\n" +
            "On next launch:\n" +
            "• previously running strategies can be restored\n" +
            "• paused strategies remain paused\n\n" +
            "If the file cannot be decrypted, the app starts without loading saved strategies."
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

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        for (String[] faq : FAQS) {
            content.add(buildFaqPanel(faq[0], faq[1]));
            content.add(Box.createVerticalStrut(14));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBorder(new EmptyBorder(12, 24, 16, 24));
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close);
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
}
