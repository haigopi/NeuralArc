package com.neuralarc.ui;

import javax.swing.JComponent;

/**
 * Hover policy for dark header buttons. When a button is flashing (e.g. an active Capture/Liquidate
 * Portfolio monitoring pulse), hover/press restyling is suppressed so the flashing animation stays
 * clean and consistent. The flashing state is signalled by the {@code flashingSuppressesHover}
 * client property.
 */
final class ButtonHoverPolicy {
    static final String FLASHING_PROPERTY = "flashingSuppressesHover";

    private ButtonHoverPolicy() {
    }

    static boolean hoverEnabled(JComponent button) {
        return button != null && !Boolean.TRUE.equals(button.getClientProperty(FLASHING_PROPERTY));
    }
}
