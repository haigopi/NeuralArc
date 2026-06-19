package com.neuralarc.vwap;

public final class VwapColumns {
    public static final String[] COLUMNS = {
            "Company Name", "Current Price", "VWAP", "Discount %", "Day Change %", "Average Volume",
            "Relative Volume", "Previous Close", "50-Day MA", "200-Day MA", "Strategy Score",
            "Planned Entry Price", "Stop Loss %", "Stop Loss Price", "Target (VWAP)", "Reversion Upside %",
            "Status", "Mode", "Added Time", "Actions"
    };
    public static final String[] ACTIONS = {"Review", "Add Alert", "Place Limit Buy", "Remove", "Move to Another Strategy"};
    private VwapColumns() {}
}
