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
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;

public class HelpDialog extends JDialog {
    private static final Color TAB_CONTENT_BORDER = new Color(200, 206, 214);

    private static final Font HEADING_FONT = FontLoader.ui(Font.BOLD, 13);
    private static final Font BODY_FONT    = FontLoader.ui(Font.PLAIN, 13);

    private static final String[][] CUSTOMER_FAQS = {
        {
            "Strategies - What can NeuralArc do for me?",
            "NeuralArc helps you turn a stock idea into a repeatable plan.\n\n" +
            "- You define the entry price, quantity, sell target, stop loss, and optional staged buy levels.\n" +
            "- The app monitors those rules locally and shows the current state in the strategy grid.\n" +
            "- You can start in simulation mode, review behavior, then promote a strategy to live only when you are ready.\n\n" +
            "The app is a decision-support and execution utility. It does not guarantee profit and it does not remove trading risk."
        },
        {
            "Strategies - What is Auto Analyze?",
            "Auto Analyze gives short-term and long-term recommendations using recent market data.\n\n" +
            "- Today's Snapshot shows the latest available price, open, high, low, and close context.\n" +
            "- Short-Term Recommendation focuses on recent two-week behavior, expected dips, trend, volume, risk/reward, and confidence.\n" +
            "- Long-Term Recommendation uses broader ranges, moving averages, ATR, market mode, and behavior-adjusted entry pricing.\n\n" +
            "You can apply a recommendation to the Current Strategy tab, review every value, and then choose whether to save."
        },
        {
            "Strategies - What are Loss Buy Levels?",
            "Loss Buy Levels are optional staged entries below your base buy price.\n\n" +
            "- Enable them when you want the strategy to add more shares at lower prices.\n" +
            "- Disable them when you want only the initial buy and no average-down behavior.\n" +
            "- The strategy will not trigger those rules when the feature is disabled.\n\n" +
            "Use these levels carefully because they can increase total exposure."
        },
        {
            "Strategies - What happens after a sell?",
            "The result depends on the rule and your strategy settings.\n\n" +
            "- Target sell and profit-hold exits can re-initiate the strategy when repeat cycle is enabled and the position is fully closed.\n" +
            "- Stop-loss and defensive close exits complete the cycle and do not auto-repeat.\n" +
            "- Manual sell exits can re-initiate only when repeat cycle is enabled.\n\n" +
            "If Alpaca still shows shares after an exit order, the grid should treat the strategy as still needing monitoring."
        },
        {
            "Application - What should I watch on the main screen?",
            "The main grid is the operator console.\n\n" +
            "- Stock Price, Shares, Avg Cost, Market Value, and P&L show current or completed position context.\n" +
            "- The polling bar shows when the next rule check is due or when a broker request is in progress.\n" +
            "- Broker, Market, Trade Stream, CPU, Memory, and total Market Value are shown in the bottom status bar.\n\n" +
            "Rows with no position can still show latest price when available."
        },
        {
            "Application - What are the Sell buttons and Portfolio Actions?",
            "Each strategy row can show a Sell button when that strategy has an open position.\n\n" +
            "- Sell submits a manual limit sell for that strategy after confirmation.\n" +
            "- Portfolio Actions can sell profitable positions, all open positions, or losing positions after confirmation.\n" +
            "- Cancel All Pending Limit Buys cancels pending limit buy orders without closing open positions or sell orders.\n" +
            "- Promote All to Live validates eligible paper strategies and creates live strategies using the same promotion rules as the row action.\n" +
            "- These actions use Alpaca and then refresh the grid and status values.\n\n" +
            "Review the confirmation dialog before submitting any sell action."
        },
        {
            "Application - What does Kill Switch do?",
            "Kill Switch is for stopping strategy activity quickly.\n\n" +
            "- It pauses active strategies.\n" +
            "- It stops polling countdowns.\n" +
            "- It saves the updated local state.\n\n" +
            "It does not automatically liquidate positions. Use Portfolio Actions or the row Sell button if you want to submit sell orders."
        },
        {
            "Application - What should I do if Trade Stream shows an error?",
            "Trade Stream is the Alpaca WebSocket connection that delivers order and trade updates.\n\n" +
            "- If the bottom status bar shows Trade Stream: error, click the Reconnect link beside it.\n" +
            "- If reconnect fails, the app shows the stream error in a dialog.\n" +
            "- Open Settings, verify Alpaca credentials, and save if the error mentions authorization or missing credentials.\n" +
            "- Check your internet connection if the error looks like a network timeout or dropped connection.\n\n" +
            "Polling still helps reconcile strategy state, but reconnecting the stream restores faster order update handling."
        },
        {
            "Settings - What do I need before using the app?",
            "You need an Alpaca account and API credentials.\n\n" +
            "- Use Paper credentials first to validate the app flow without real money.\n" +
            "- Paste the key and secret into Settings and verify the connection.\n" +
            "- Save Settings before expecting changes to affect the running app.\n\n" +
            "Unsaved Settings changes are not applied."
        },
        {
            "Settings - What are market-hours controls?",
            "The app can reduce broker API usage when markets are closed.\n\n" +
            "- Auto pause polling when market is closed pauses strategy evaluation outside tradable sessions.\n" +
            "- Extended-hours trading allows eligible orders during pre-market and after-hours windows.\n" +
            "- When extended hours is off, only regular market hours are considered tradable for automated polling.\n\n" +
            "Manual limit buy placement is still allowed after hours; Alpaca decides whether the order is accepted."
        },
        {
            "Live Trading - How should I move from simulation to live?",
            "Live trading is intentionally explicit.\n\n" +
            "- Switch the app connection to Live mode only when live trading is enabled and credentials are verified.\n" +
            "- Use Preview Live Promotion to review the paper strategy, checklist, P&L, and account state.\n" +
            "- Promote to Live creates a live copy and archives the paper strategy after the live base order succeeds.\n\n" +
            "Do not treat paper results as a guarantee of live results."
        }
    };

    private static final String[][] TECHNICAL_FAQS = {
        {
            "Strategy Engine - How are rules evaluated?",
            "Each strategy is evaluated as a state machine.\n\n" +
            "- Base buy starts the cycle.\n" +
            "- Buy Level 1 waits for the base buy to fully fill.\n" +
            "- Buy Level 2 waits for Buy Level 1 to fully fill.\n" +
            "- Stop loss, target sell, profit hold, optional loss exit, and manual exit are handled as separate stages.\n" +
            "- Partial fills do not advance staged buy rules.\n\n" +
            "Rule evaluation is logged so you can see which rules were satisfied, skipped, or not satisfied."
        },
        {
            "Polling - How does timing work?",
            "Polling is per strategy and runs off the Swing UI thread.\n\n" +
            "- The grid animation follows the configured polling interval.\n" +
            "- While a broker call is in flight, the grid shows Polling.\n" +
            "- When the market is closed and auto-pause is enabled, closed-market checks are throttled to reduce API usage.\n" +
            "- UI rendering reads cached snapshots rather than calling Alpaca from table renderers.\n\n" +
            "This keeps the UI responsive while strategies continue to be evaluated in the background."
        },
        {
            "Market Data - How are prices shown when the market is closed?",
            "The app refreshes visible stock prices in batches where possible.\n\n" +
            "- Positions are loaded by account mode, Paper or Live.\n" +
            "- Latest prices can be fetched in one multi-symbol call per mode.\n" +
            "- Cached position snapshots are then used by the grid, selected position panel, and status summaries.\n\n" +
            "This avoids one broker request per table cell."
        },
        {
            "Alpaca - Which APIs are involved?",
            "The app uses Alpaca Trading APIs for account, orders, open orders, positions, and request IDs.\n\n" +
            "It also uses Alpaca market data for latest prices and Auto Analyze historical candles.\n\n" +
            "Recent Alpaca X-Request-ID values are persisted locally and can be attached to support requests so issues are easier to trace."
        },
        {
            "Trade Stream - What does the stream status mean?",
            "Trade Stream shows the WebSocket lifecycle.\n\n" +
            "- Connecting means the stream is opening.\n" +
            "- Authorized means Alpaca accepted authentication.\n" +
            "- Listening means trade updates are subscribed.\n" +
            "- Trade update means the app received an order event.\n" +
            "- Error means the stream failed and the bottom status bar provides a Reconnect link.\n\n" +
            "Streaming events update local order state and trigger a background position refresh for the affected symbol. If reconnect fails, the app displays the WebSocket error so you can correct credentials or connectivity."
        },
        {
            "Persistence - Where is state stored?",
            "The app stores strategy state locally so it can recover after restart.\n\n" +
            "- Strategies, orders, and execution events are stored in the local application database.\n" +
            "- App settings are stored locally and loaded only after Save.\n" +
            "- Local state is reconciled with Alpaca open positions and orders on startup.\n\n" +
            "This local-first model is why the app can restore strategies instead of starting empty every time."
        },
        {
            "Security - How are credentials handled?",
            "API keys and secrets are saved locally and protected before being persisted.\n\n" +
            "- They are not added to telemetry events.\n" +
            "- They are not included in support emails.\n" +
            "- Support diagnostics can include logs and Alpaca request IDs, but not secrets.\n\n" +
            "Use Paper credentials for initial testing and keep Live credentials separate."
        },
        {
            "Telemetry and Support - What gets sent?",
            "Telemetry is operational and opt-in.\n\n" +
            "- It is intended for app reliability and workflow events.\n" +
            "- Contact, bug, and feature request emails are sent through the configured Mailjet support flow.\n" +
            "- Bug reports can attach recent logs and recent Alpaca request IDs.\n\n" +
            "Customer email from Settings is used for reply/copy behavior, not as an unverified Mailjet sender."
        },
        {
            "Updates - How does software update checking work?",
            "Check for Updates reads the configured GitHub latest release URL.\n\n" +
            "- The app compares the installed version with the latest release tag.\n" +
            "- If a newer release is available, it opens the matching installer asset for your platform.\n" +
            "- macOS and Windows packages are attached to GitHub Releases as downloadable assets.\n\n" +
            "The app does not silently replace itself while running."
        },
        {
            "System Behavior - What happens during sleep or shutdown?",
            "If the computer sleeps, active Java timers and network streams stop running until the machine wakes.\n\n" +
            "After wake, the app resumes, reconnects, and reconciles with Alpaca.\n\n" +
            "For continuous operation, keep the machine awake and connected."
        }
    };

    private static final String[][] STRATEGY_DIALOG_GUIDE = {
        {
            "Add New Stock Strategy - What is this dialog for?",
            "Use Add New Stock Strategy to create or edit a rule-based plan for one stock.\n\n" +
            "- Current Strategy is where you enter the actual values the app will monitor.\n" +
            "- Auto Analyze is a helper that can calculate suggested values from market data.\n" +
            "- Saving the strategy stores the configuration locally and makes it available in the main grid.\n\n" +
            "Auto Analyze recommendations are never traded automatically. Review the values before saving."
        },
        {
            "Current Strategy - Identity and mode fields",
            "These fields define what the strategy belongs to and how it should be executed.\n\n" +
            "- Symbol: The stock ticker, such as TSLA, AAPL, or QCOM. Symbols are normalized to uppercase.\n" +
            "- Paper trading mode: Keeps the strategy in simulation mode with Alpaca paper credentials.\n" +
            "- Promote to Live: Creates a live copy only after a live promotion preview and confirmation.\n\n" +
            "Paper and Live strategies are kept separate so switching app mode does not silently convert saved strategies."
        },
        {
            "Current Strategy - Base buy fields",
            "Base buy is the first entry rule for the strategy.\n\n" +
            "- Base Buy Price: The limit price where the first buy order should be placed.\n" +
            "- Base Buy Quantity: The number of shares to buy at the base price.\n" +
            "- Polling Interval: How often the app checks the strategy rules when polling is active.\n\n" +
            "Use a realistic polling interval. Very aggressive intervals can increase API usage and system load."
        },
        {
            "Risk Controls - Stop loss",
            "Stop loss is the defensive exit rule.\n\n" +
            "- Enable Stop Loss: Turns stop-loss monitoring on or off.\n" +
            "- Stop Loss Price: The limit area where the app should attempt to exit when price moves against the strategy.\n\n" +
            "Disabling stop loss removes an important risk control. Only do that when you have another exit plan."
        },
        {
            "Profit Controls - Sell Trigger",
            "Sell Trigger is a local application-side profit rule.\n\n" +
            "- Enable Sell Trigger: Monitors price during polling and places a sell only after the trigger price is reached.\n" +
            "- Sell Trigger Price: The price where the app should submit the configured sell order.\n\n" +
            "Enabling Sell Trigger does not place an Alpaca sell order immediately. Manual selling remains available."
        },
        {
            "Profit Controls - Automatic Stop Sell",
            "Automatic Stop Sell places broker-side protection only after profit activation is reached.\n\n" +
            "- Profit Activation Type: Percentage gain or fixed dollar gain from the current average entry price.\n" +
            "- Profit Activation Value: The gain required before the app submits Alpaca trailing stop protection.\n" +
            "- Broker Trailing Type and Value: The Alpaca trailing stop distance after activation.\n\n" +
            "The app checks for existing local and Alpaca sell orders before submitting a new trailing stop."
        },
        {
            "Risk Controls - Loss Buy Levels",
            "Loss Buy Levels are optional staged buys below the base buy.\n\n" +
            "- Enable Loss Buy Levels: Allows staged buy rules to run.\n" +
            "- Loss Buy 1 Price and Quantity: First additional buy level after the base buy fills.\n" +
            "- Loss Buy 2 Price and Quantity: Second additional buy level after Loss Buy 1 fills.\n\n" +
            "These rules can reduce average cost, but they also increase position size and risk."
        },
        {
            "Profit Hold Option",
            "Profit Hold can delay the final sell so the strategy can try to capture more upside.\n\n" +
            "- Enable Profit Hold: Turns trailing profit behavior on.\n" +
            "- Profit Activation Type and Value: The threshold where Profit Hold starts trailing.\n" +
            "- Profit hold type: Choose percent trailing or fixed amount trailing.\n" +
            "- Percent trailing: Exits after price pulls back by the configured percent from the observed high.\n" +
            "- Fixed amount trailing: Exits after price pulls back by the configured dollar amount.\n\n" +
            "Profit Hold is application-side. It does not require Sell Trigger to be enabled, and manual selling remains available."
        },
        {
            "Profit Controls - Strategy Selection",
            "Only one automated Profit Control strategy can be active at a time.\n\n" +
            "- Sell Trigger sells when the configured trigger price is reached.\n" +
            "- Automatic Stop Sell places Alpaca trailing protection after profit activation.\n" +
            "- Profit Hold trails locally after profit activation.\n\n" +
            "Manual selling is always available regardless of the selected automated strategy."
        },
        {
            "Auto Analyze - Inputs",
            "Auto Analyze uses market data to generate decision-support values.\n\n" +
            "- Symbol: The ticker to analyze.\n" +
            "- Months back: How much historical data to inspect for range and long-term calculations.\n" +
            "- Intraday interval: The intraday candle interval used for today's snapshot and near-term context.\n" +
            "- Run Auto Analyze: Fetches data and refreshes the recommendation sections.\n\n" +
            "A valid Alpaca connection is required for live market and historical data."
        },
        {
            "Auto Analyze - Today's Snapshot",
            "Today's Snapshot gives quick market context before you apply recommendations.\n\n" +
            "- Stock Price: Latest reliable price available from market data.\n" +
            "- Open: Today's opening price when available.\n" +
            "- High So Far: Highest price seen so far today.\n" +
            "- Low So Far: Lowest price seen so far today.\n" +
            "- Close: Most recent confirmed close when available.\n\n" +
            "Use this section to understand whether a recommendation is close to the current market."
        },
        {
            "Auto Analyze - Recommendation sections",
            "Short-Term and Long-Term recommendations generate strategy values without saving them.\n\n" +
            "- Base Buy Price: Suggested entry price.\n" +
            "- Buy Level 1 and Buy Level 2: Suggested staged entries when Loss Buy Levels are enabled.\n" +
            "- Stop Loss: Suggested risk boundary.\n" +
            "- Sell Price and Targets: Suggested profit targets.\n" +
            "- Confidence Score: A 0 to 100 score based on trend, volume, risk/reward, and market mode.\n" +
            "- Recommendation: BUY, WATCH, or AVOID.\n\n" +
            "Apply to Current Strategy copies values into the Current Strategy tab, but it does not save or place trades."
        }
    };

    private static final String[][] SETTINGS_DIALOG_GUIDE = {
        {
            "Settings - What is this dialog for?",
            "Settings controls account connection, app mode, telemetry, market-hours behavior, and local data options.\n\n" +
            "- Changes are staged while the dialog is open.\n" +
            "- Save applies the changes to the running app.\n" +
            "- Cancel closes the dialog without applying unsaved changes.\n\n" +
            "This prevents accidental mode or credential changes from affecting the grid before you save."
        },
        {
            "User Email",
            "User Email identifies who is contacting support and where replies should go.\n\n" +
            "- Contact Us, Bug Report, and Request New Feature include this email in the support message body.\n" +
            "- Support emails are sent through the app's configured support sender.\n" +
            "- The customer email is not used as an unverified Mailjet sender.\n\n" +
            "Keep this current if you want support responses to reach the right inbox."
        },
        {
            "Alpaca API Details",
            "These fields connect NeuralArc to Alpaca.\n\n" +
            "- Broker: Selects the broker integration. Alpaca is the current trading integration.\n" +
            "- Application mode: Selects Paper or Live connection mode for the app session.\n" +
            "- API key: Alpaca API key for the selected mode.\n" +
            "- API secret: Alpaca API secret for the selected mode.\n" +
            "- Verify Connection: Confirms that the selected credentials can reach Alpaca.\n\n" +
            "Use different Paper and Live API keys. Live mode should be enabled only when you intend to trade live."
        },
        {
            "Save Credentials Locally",
            "This option controls whether Alpaca credentials are stored on this machine.\n\n" +
            "- Enabled: Credentials are saved locally so the app can reconnect after restart.\n" +
            "- Disabled: You may need to enter credentials again later.\n\n" +
            "Credentials should never be shared in logs, telemetry, or support messages."
        },
        {
            "Telemetry",
            "Telemetry is for app reliability and operational diagnostics.\n\n" +
            "- Enable telemetry: Allows the app to publish anonymized operational events.\n" +
            "- Endpoint: The configured telemetry destination.\n\n" +
            "Telemetry does not include API keys or secrets, and it remains opt-in."
        },
        {
            "Trading Hours",
            "Trading Hours controls how the app behaves around market sessions.\n\n" +
            "- Auto pause polling when market is closed: Reduces API usage by pausing rule evaluation outside tradable sessions.\n" +
            "- Enable extended-hours trading: Allows eligible Alpaca orders to include extended-hours behavior.\n" +
            "- Allow multiple strategies for the same symbol: Lets you create more than one strategy for the same ticker.\n\n" +
            "Manual limit orders can still be placed when markets are closed. Polling is what gets reduced."
        },
        {
            "Data Management and Reset Options",
            "Settings also includes maintenance actions for local app state.\n\n" +
            "- Reset can clear locally saved app data depending on the option selected.\n" +
            "- Reload or reconciliation actions restore local strategy context from Alpaca where supported.\n" +
            "- These tools are intended for recovery, cleanup, and support-guided troubleshooting.\n\n" +
            "Review reset confirmations carefully because local state changes can affect what the grid restores."
        }
    };

    private static final String[][] OTHER_DIALOG_GUIDE = {
        {
            "Preview Live Promotion",
            "Preview Live Promotion is the review gate before a paper strategy becomes live.\n\n" +
            "- It shows the strategy values that will be cloned to Live.\n" +
            "- It shows realized and unrealized P&L context where available.\n" +
            "- It lets you choose whether paper positions should be closed or kept.\n" +
            "- Promote to Live creates the live strategy only after confirmation.\n\n" +
            "The paper strategy is archived after successful live promotion so the two modes remain clear."
        },
        {
            "Contact Us",
            "Contact Us is for general customer support.\n\n" +
            "- Your configured customer email is included in the message details.\n" +
            "- The app uses the support email flow to send the message.\n" +
            "- Keep the message concise and include the symbol or workflow if the question is strategy-specific.\n\n" +
            "Do not paste API secrets into support messages."
        },
        {
            "Submit Bug",
            "Submit Bug is for reporting problems in the app.\n\n" +
            "- The app can include useful diagnostic context such as recent logs and Alpaca request IDs.\n" +
            "- Request IDs help trace Alpaca API calls when support investigates an issue.\n" +
            "- Secrets and API keys should not be included.\n\n" +
            "Use this when something fails, looks incorrect, or does not match Alpaca."
        },
        {
            "Request New Feature",
            "Request New Feature is for product ideas and workflow improvements.\n\n" +
            "- Describe what you are trying to accomplish.\n" +
            "- Include the trading workflow, strategy type, or setting that would benefit.\n" +
            "- The app packages the request with your customer email for follow-up.\n\n" +
            "Clear use cases are easier to prioritize than broad feature names."
        },
        {
            "First Run Onboarding",
            "First Run Onboarding appears for new users and explains the minimum setup path.\n\n" +
            "- Create or prepare an Alpaca account.\n" +
            "- Add API credentials in Settings.\n" +
            "- Start in simulation mode.\n" +
            "- Add a strategy and review how the grid reports status.\n\n" +
            "After completion or skip, onboarding is not shown again unless local app state is reset."
        },
        {
            "About Dialog",
            "About shows app identity and version information.\n\n" +
            "- App name and logo confirm the running product.\n" +
            "- Version information helps support match reports to a release.\n" +
            "- Legal and product metadata may be shown depending on the build.\n\n" +
            "Use About when checking whether an update installed successfully."
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
        tabs.addTab("Customer Benefits", buildFaqScrollPane(CUSTOMER_FAQS));
        tabs.addTab("Technical Highlights", buildFaqScrollPane(TECHNICAL_FAQS));
        tabs.addTab("Strategy Dialog", buildFaqScrollPane(STRATEGY_DIALOG_GUIDE));
        tabs.addTab("Settings Dialog", buildFaqScrollPane(SETTINGS_DIALOG_GUIDE));
        tabs.addTab("Other Dialogs", buildFaqScrollPane(OTHER_DIALOG_GUIDE));
        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBorder(new EmptyBorder(12, 24, 16, 24));
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(e -> setVisible(false));
        footer.add(close);
        add(footer, BorderLayout.SOUTH);

        pack();
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(760, Math.min(getPreferredSize().width, screenBounds.width - 48));
        int h = Math.max(600, Math.min(getPreferredSize().height, (int) (screenBounds.height * 0.92)));
        setSize(w, h);
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
