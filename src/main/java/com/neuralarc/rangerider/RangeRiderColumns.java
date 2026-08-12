package com.neuralarc.rangerider;

public final class RangeRiderColumns {
    public static final String[] COLUMNS = {
            "Company Name", "Reference Close", "Average Open", "Average High", "Average Low",
            "Average Daily Range %", "Typical Dip %", "Typical Rally %", "Range Stability %",
            "Entry Touch Rate %", "Same-Day Fill Rate %", "Sessions Analyzed", "Average Volume",
            "Relative Volume", "Strategy Score", "Planned Buy Price", "Planned Sell Price",
            "Expected Gain %", "Stop Loss %", "Stop Loss Price", "Status", "Mode", "Added Time", "Actions"
    };
    public static final String[] ACTIONS = {"Review", "Add Alert", "Place Limit Buy", "Remove", "Move to Another Strategy"};
    private RangeRiderColumns() {}
}
