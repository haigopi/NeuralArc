package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JSpinner;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharesAndTimeInForceDialogTest {
    @Test
    void globalValuesReachTheIncludedRowsWithoutAnExtraApplyStep() {
        // Regression guard: the global values only applied after a separate "Apply to Included Rows" click,
        // so setting 5 shares / GTC and pressing Apply Changes changed nothing ("Nothing was changed").
        SharesAndTimeInForcePlan plan = new SharesAndTimeInForcePlan(List.of(
                new SharesAndTimeInForcePlan.Row("a", "AAPL", true, new BigDecimal("180"), 1, TimeInForce.DAY),
                new SharesAndTimeInForcePlan.Row("m", "MSFT", true, new BigDecimal("400"), 1, TimeInForce.DAY)));
        plan.rows().get(1).included = false;
        SharesAndTimeInForceDialog dialog = new SharesAndTimeInForceDialog(null, plan, "Growth");

        find(dialog, JSpinner.class).get(0).setValue(5);
        find(dialog, JComboBox.class).get(0).setSelectedItem(TimeInForce.GTC);

        List<SharesAndTimeInForcePlan.Change> changes = plan.changes();
        assertEquals(1, changes.size(), "the excluded row stays as it was");
        assertEquals(5, changes.get(0).quantity());
        assertEquals(TimeInForce.GTC, changes.get(0).timeInForce());
        dialog.dispose();
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(find(container, type));
            }
        }
        return found;
    }
}
