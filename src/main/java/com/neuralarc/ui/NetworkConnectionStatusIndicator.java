package com.neuralarc.ui;

import com.neuralarc.api.ApiCallMetrics;
import com.neuralarc.util.SvgIconLoader;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class NetworkConnectionStatusIndicator {
    private static final int ICON_SIZE = 16;
    private static final int PROBE_TIMEOUT_MILLIS = 1500;
    private static final long PROBE_INTERVAL_SECONDS = 5L;
    private static final Color ONLINE_COLOR = new Color(108, 201, 168);
    private static final Color OFFLINE_COLOR = new Color(220, 44, 44);
    private static final Color OFFLINE_DIM_COLOR = new Color(120, 28, 28);

    private final JLabel label = new JLabel();
    private final StatusBarPresenter presenter;
    private final ScheduledExecutorService probeExecutor;
    private final AtomicBoolean probeInFlight = new AtomicBoolean(false);
    private Timer blinkTimer;
    private boolean blinkOn = true;
    private volatile boolean online = true;

    NetworkConnectionStatusIndicator(StatusBarPresenter presenter) {
        this.presenter = presenter;
        label.setPreferredSize(new Dimension(24, 20));
        label.setMinimumSize(new Dimension(24, 20));
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        label.getAccessibleContext().setAccessibleName("Internet connection status");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showNetworkUsage();
            }
        });
        applyNetworkStatus(presenter.presentNetworkStatus(true));

        probeExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-network-status");
            thread.setDaemon(true);
            return thread;
        });
        probeExecutor.scheduleWithFixedDelay(this::probeNetworkStatus, 0, PROBE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    JComponent component() {
        return label;
    }

    void shutdown() {
        if (blinkTimer != null) {
            blinkTimer.stop();
        }
        probeExecutor.shutdownNow();
    }

    private void probeNetworkStatus() {
        if (!probeInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            updateOnlineStatus(hasUsableNetworkInterface() && canOpenInternetSocket());
        } finally {
            probeInFlight.set(false);
        }
    }

    private boolean hasUsableNetworkInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return false;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp()
                        && !networkInterface.isLoopback()
                        && !networkInterface.isVirtual()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private boolean canOpenInternetSocket() {
        return canConnect("1.1.1.1", 443) || canConnect("8.8.8.8", 53);
    }

    private boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateOnlineStatus(boolean newOnline) {
        if (online == newOnline) {
            return;
        }
        online = newOnline;
        SwingUtilities.invokeLater(this::applyOnlineStatus);
    }

    private void applyOnlineStatus() {
        StatusBarPresenter.NetworkStatusViewModel model = presenter.presentNetworkStatus(online);
        applyNetworkStatus(model);
    }

    private void applyNetworkStatus(StatusBarPresenter.NetworkStatusViewModel model) {
        if (model.blink()) {
            startOfflineBlink(model);
        } else {
            stopOfflineBlink();
            renderIcon(model, model.tone() == StatusBarPresenter.Tone.ERR ? OFFLINE_COLOR : ONLINE_COLOR);
        }
        label.setToolTipText(TooltipStyler.text(model.tooltip() + " Click to view API usage."));
    }

    private void showNetworkUsage() {
        ApiCallMetrics.Snapshot snapshot = ApiCallMetrics.snapshot();
        String since = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(snapshot.since());
        String message = "<html><body style='width:320px'>"
                + "<b>Alpaca API Usage</b><br><br>"
                + "Total API calls made: <b>" + snapshot.total() + "</b><br>"
                + "Succeeded: <b>" + snapshot.succeeded() + "</b><br>"
                + "Failed: <b>" + snapshot.failed() + "</b><br>"
                + "Success rate: <b>" + String.format("%.1f%%", snapshot.successRatePercent()) + "</b>"
                + "<br><br><span style='color:#8A919C;'>Failed counts calls that could not reach the broker "
                + "(timeouts / network errors) or returned a server error (HTTP 5xx). "
                + "Counting since " + since + ".</span>"
                + "</body></html>";
        Object[] options = {"Close", "Reset counters"};
        int choice = JOptionPane.showOptionDialog(
                SwingUtilities.getWindowAncestor(label),
                message,
                "Network Usage",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == 1) {
            ApiCallMetrics.reset();
        }
    }

    private void startOfflineBlink(StatusBarPresenter.NetworkStatusViewModel model) {
        blinkOn = true;
        renderIcon(model, OFFLINE_COLOR);
        if (blinkTimer == null) {
            blinkTimer = new Timer(500, ignored -> {
                blinkOn = !blinkOn;
                renderIcon(model, blinkOn ? OFFLINE_COLOR : OFFLINE_DIM_COLOR);
            });
        }
        if (!blinkTimer.isRunning()) {
            blinkTimer.start();
        }
    }

    private void stopOfflineBlink() {
        if (blinkTimer != null) {
            blinkTimer.stop();
        }
        blinkOn = true;
    }

    private void renderIcon(StatusBarPresenter.NetworkStatusViewModel model, Color color) {
        label.setIcon(SvgIconLoader.load(model.iconPath(), ICON_SIZE, color));
    }
}
