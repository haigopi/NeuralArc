package com.neuralarc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiLogUrlsTest {
    @Test
    void decodesEncodedCommasInASymbolList() {
        assertEquals("https://data.alpaca.markets/v2/stocks/trades/latest?symbols=AAPL,AMZN,META",
                ApiLogUrls.readable("https://data.alpaca.markets/v2/stocks/trades/latest?symbols=AAPL%2CAMZN%2CMETA"));
    }

    @Test
    void decodesEachValueButKeepsTheParameterStructure() {
        assertEquals("/v2/orders?status=open&symbols=A,B&limit=50",
                ApiLogUrls.readable("/v2/orders?status=open&symbols=A%2CB&limit=50"));
    }

    @Test
    void decodesSpacesAndNonAsciiText() {
        assertEquals("/x?q=a b&n=é", ApiLogUrls.readable("/x?q=a%20b&n=%C3%A9"));
    }

    @Test
    void leavesTheEncodedValueWhenTheEscapeIsMalformed() {
        assertEquals("/x?q=100%", ApiLogUrls.readable("/x?q=100%"));
    }

    @Test
    void keepsALogRecordOnOneLine() {
        assertEquals("/x?q=a b", ApiLogUrls.readable("/x?q=a%0Ab"));
    }

    @Test
    void passesThroughEndpointsWithoutAQuery() {
        assertEquals("/v2/account", ApiLogUrls.readable("/v2/account"));
        assertEquals("", ApiLogUrls.readable(null));
    }
}
