package com.neuralarc.diphunter;

public final class DipHunterColumns {
    public static final String[] COLUMNS = {
            "Company Name", "Pullback %", "Day Change %", "Average Volume", "Relative Volume", "Current Price",
            "Previous Close", "Recent High", "20-Day MA", "50-Day MA", "Strategy Score", "Bounce Confirmation",
            "Planned Entry Price", "Stop Loss %", "Stop Loss Price", "Take Profit %", "Take Profit Price",
            "Status", "Mode", "Added Time", "Actions"
    };
    public static final String[] ACTIONS = {"Review", "Add Alert", "Place Limit Buy", "Remove", "Move to Another Strategy"};
    private DipHunterColumns() {}
}
