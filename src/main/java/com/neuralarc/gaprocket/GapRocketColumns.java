package com.neuralarc.gaprocket;

public final class GapRocketColumns {
    public static final String[] COLUMNS = {
            "Company Name", "Gap %", "Premarket Volume", "Relative Volume", "Current Price", "Previous Close",
            "Premarket High", "Premarket Low", "Catalyst Type", "Catalyst Summary", "Strategy Score", "Entry Style",
            "Opening Range Duration", "Planned Entry Price", "Stop Loss %", "Stop Loss Price", "Take Profit %",
            "Take Profit Price", "Status", "Mode", "Added Time", "Actions"
    };
    public static final String[] ACTIONS = {"Review", "Add Alert", "Place Limit Buy", "Remove", "Move to Another Strategy"};
    private GapRocketColumns() {}
}
