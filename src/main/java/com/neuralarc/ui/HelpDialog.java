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
            "User Email: Your account identifier. A unique User ID is derived from it and used for analytics.\n\n" +
            "API Key / API Secret: Credentials provided by your broker (e.g., Alpaca). These are encrypted " +
            "with AES-256-GCM before being written to disk. Never share them.\n\n" +
            "Broker: Choose MOCK for safe simulation without any real broker connection, or ALPACA to connect " +
            "to a real Alpaca account.\n\n" +
            "Save Credentials Locally: When checked, your API key and secret are stored encrypted at " +
            "~/.neuralarc/credentials.properties and reloaded automatically next time you open the app.\n\n" +
            "Analytics Endpoint: URL where anonymised trading events are sent (opt-in only). Leave blank or " +
            "use the default if you are not hosting your own analytics server.\n\n" +
            "Enable Telemetry: Must be explicitly checked before any data is transmitted. No data leaves " +
            "your machine unless this is on."
        },
        {
            "📄  Paper Trading Mode — What is it and when should I use it?",
            "Paper Trading simulates buying and selling stocks without using real money or connecting to a " +
            "live broker.\n\n" +
            "• Orders are filled against a built-in mock price feed that oscillates predictably — ideal for " +
            "testing strategy logic.\n" +
            "• P&L figures (realized, unrealized, market value) are all virtual. No real funds are at risk.\n" +
            "• Position state, average cost, and rule triggers behave exactly the same as in live mode, so " +
            "you can validate your strategy parameters (buy price, stop activation, sell trigger, loss levels) " +
            "without consequences.\n\n" +
            "Recommendation: Always run in Paper mode first until you are confident your strategy behaves as " +
            "intended across multiple price cycles before switching to Live."
        },
        {
            "📈  Live Trading Mode — What changes and what risks exist?",
            "Live mode routes real orders to your connected broker using the API credentials in Settings.\n\n" +
            "• Real money is used. Losses are real.\n" +
            "• Order fills depend on market liquidity and broker execution latency — fills may differ from the " +
            "simulated mock prices.\n" +
            "• Ensure your API key has order-placement permissions on the broker side.\n" +
            "• Use the smallest quantities possible when first testing with real funds.\n" +
            "• The app sends a STRATEGY_STARTED analytics event when a live strategy begins, if telemetry is " +
            "enabled.\n\n" +
            "The app does not handle margin, short selling, or fractional shares in the current version. " +
            "All orders are whole-share market buys or sells."
        },
        {
            "🦙  Alpaca — What is it and how do I connect?",
            "Alpaca (alpaca.markets) is a commission-free stock trading API designed for developers and " +
            "algorithmic traders.\n\n" +
            "To connect NeuralArc to Alpaca:\n" +
            "1. Sign up at alpaca.markets and create a Paper or Live account.\n" +
            "2. Generate an API Key and API Secret in the Alpaca dashboard.\n" +
            "3. Enter them in Settings → API Details and press Encrypt & Save.\n" +
            "4. Select ALPACA as the broker.\n" +
            "5. Click Test Connection — a green status confirms the API is reachable.\n\n" +
            "Important: The ALPACA integration in this version is a stub — it compiles and routes calls " +
            "correctly but returns empty/mock responses. Full live Alpaca execution requires completing the " +
            "AlpacaTradingApi implementation with real HTTP calls to api.alpaca.markets.\n\n" +
            "Use MOCK broker for all reliable testing until the Alpaca integration is fully implemented."
        },
        {
            "🛡️  Strategy Persistence — Where are my strategies saved?",
            "When you add or modify a strategy, it is immediately saved to:\n" +
            "   ~/.neuralarc/strategies.dat\n\n" +
            "The file is AES-256-GCM encrypted using a key derived from your user email. On next launch, " +
            "after you successfully run Test Connection, your strategies are automatically restored:\n\n" +
            "• Strategies that were Running at close are auto-started.\n" +
            "• Strategies that were Paused at close are restored in paused state — you can resume them manually.\n\n" +
            "If the file is corrupt or the passphrase does not match (e.g., you changed your email), " +
            "strategies are not loaded and a clean slate is presented."
        },
        {
            "🔒  Privacy — Nano Jetson Home-Deployed AI (Future)",
            "Coming Soon: NeuralArc will support privacy-preserving on-device AI inference using NVIDIA Jetson Nano.\n\n" +
            "Future Feature:\n" +
            "• Deploy your trading models directly on a Jetson Nano running in your home — no cloud calls.\n" +
            "• All price data, position updates, and AI predictions stay local on your device.\n" +
            "• No sensitive trading information leaves your network.\n" +
            "• Telemetry remains opt-in and can be disabled completely for maximum privacy.\n\n" +
            "Status: Currently under development. Check back in future releases for full integration and setup " +
            "instructions. In the meantime, use MOCK broker and Paper Trading to explore NeuralArc safely."
        },
        {
            "⚠️  KILL SWITCH — Emergency Stop for All Strategies",
            "The red KILL SWITCH button in the top-right toolbar provides instant emergency stop capability.\n\n" +
            "What It Does:\n" +
            "• Stops ALL running strategies immediately (strategies already paused remain paused).\n" +
            "• Marks stopped strategies as paused, preserving their configuration.\n" +
            "• Saves all strategy state to the encrypted file automatically.\n" +
            "• Does NOT close the app — you can inspect logs and resume strategies manually if needed.\n\n" +
            "When to Use:\n" +
            "• Market emergency or flash crash — need instant position exit.\n" +
            "• Unexpected behavior detected in live trading.\n" +
            "• Manual intervention required before strategies continue.\n\n" +
            "What Happens After Kill Switch:\n" +
            "1. All running strategies are paused in place.\n" +
            "2. Event log shows '[SYMBOL] EMERGENCY STOP' for each stopped strategy.\n" +
            "3. Status bar updates to show idle/paused count.\n" +
            "4. Strategies remain in your grid and can be manually resumed later.\n" +
            "5. A KILL_SWITCH_ACTIVATED analytics event is recorded (if telemetry enabled).\n\n" +
            "Note: The Kill Switch stops polling and order execution, but does NOT sell existing positions. " +
            "Use it for immediate halt while you assess the market."
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
        close.setFocusPainted(false);
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

