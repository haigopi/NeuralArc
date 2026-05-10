package com.neuralarc.service;

import org.json.JSONObject;

public interface AlpacaScreenerClient {
    JSONObject getMarketMovers(int top) throws AlpacaScreenerException;

    JSONObject getMostActives(String by, int top) throws AlpacaScreenerException;
}
