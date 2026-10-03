package com.quant.factor.mr;

import com.quant.factor.model.StockTick;

import java.util.HashMap;
import java.util.Map;

/** Keeps the prior snapshot separate for every trading day and stock. */
public final class TickHistory {
    private final Map<String, StockTick> previous = new HashMap<>();

    public StockTick previousFor(StockTick current) {
        return previous.get(key(current));
    }

    public void remember(StockTick current) {
        previous.put(key(current), current.copy());
    }

    private static String key(StockTick tick) {
        return tick.tradingDay + '\t' + tick.code;
    }
}
