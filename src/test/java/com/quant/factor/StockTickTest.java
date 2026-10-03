package com.quant.factor;

import com.quant.factor.model.StockTick;
import org.apache.hadoop.io.Text;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StockTickTest {
    @Test
    public void parsesTheIntegerSnapshotColumnsUsedByTheFormulas() {
        StockTick tick = new StockTick();
        tick.parse(new Text(snapshot("10000")));
        assertTrue(tick.isValid());
        assertEquals("20240102", tick.tradingDay);
        assertEquals("093000", tick.tradeTime);
        assertEquals("000001", tick.code);
        assertEquals(12345L, tick.tBidVol);
        assertEquals(10000L, tick.bp[0]);
        assertEquals(10010L, tick.ap[0]);
        assertEquals(5L, tick.bv[4]);
        assertEquals(6L, tick.av[4]);
    }

    @Test
    public void rejectsBadNumbersAndShortRowsWithoutReusingOldFields() {
        StockTick tick = new StockTick();
        tick.parse(new Text(snapshot("10000")));
        assertTrue(tick.isValid());
        tick.parse(new Text(snapshot("93.9")));
        assertFalse(tick.isValid());
        tick.parse(new Text("20240102,093000"));
        assertFalse(tick.isValid());
        tick.parse(new Text(snapshot("10001")));
        assertTrue(tick.isValid());
        assertEquals(10001L, tick.bp[0]);
    }

    private static String snapshot(String firstBid) {
        String[] fields = new String[57];
        Arrays.fill(fields, "0");
        fields[0] = "20240102";
        fields[1] = "093000";
        fields[4] = "000001";
        fields[12] = "12345";
        fields[13] = "6789";
        for (int level = 0; level < 5; level++) {
            int start = 17 + level * 4;
            fields[start] = level == 0 ? firstBid : String.valueOf(10000 - level * 10);
            fields[start + 1] = String.valueOf(level + 1);
            fields[start + 2] = String.valueOf(10010 + level * 10);
            fields[start + 3] = String.valueOf(level + 2);
        }
        return String.join(",", fields);
    }
}
