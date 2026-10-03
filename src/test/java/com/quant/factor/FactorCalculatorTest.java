package com.quant.factor;

import com.quant.factor.logic.FactorCalculator;
import com.quant.factor.model.StockTick;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FactorCalculatorTest {
    @Test
    public void computesAllTwentyFactorsInCourseOrder() {
        StockTick current = tick(
                new long[]{100, 99, 98, 97, 96},
                new long[]{10, 20, 30, 40, 50},
                new long[]{102, 103, 104, 105, 106},
                new long[]{20, 20, 20, 20, 20});
        current.tBidVol = 300;
        current.tAskVol = 100;
        StockTick previous = tick(
                new long[]{99, 98, 97, 96, 95},
                new long[]{5, 10, 15, 20, 25},
                new long[]{101, 102, 103, 104, 105},
                new long[]{20, 20, 20, 20, 20});

        double[] actual = new double[20];
        FactorCalculator.calculate(current, previous, actual);
        double[] expected = {
                2, 2.0 / 101, 101, -10.0 / 30, 50.0 / 250,
                150, 100, 50, 1.5, 0.5,
                14600.0 / 150, 104, 100, 104 - 14600.0 / 150, 10,
                (50 - (20 + 10 + 20.0 / 3 + 5 + 4))
                        / (50 + (20 + 10 + 20.0 / 3 + 5 + 4)),
                1, 1, 0.75, 2.0 / 250
        };
        for (int index = 0; index < expected.length; index++) {
            assertEquals("alpha_" + (index + 1), expected[index], actual[index], 1e-9);
        }
    }

    @Test
    public void zeroDepthUsesEpsilonAndFirstTickHasZeroChanges() {
        StockTick empty = tick(new long[5], new long[5], new long[5], new long[5]);
        double[] actual = new double[20];
        FactorCalculator.calculate(empty, null, actual);
        for (int index = 0; index < actual.length; index++) {
            assertEquals("alpha_" + (index + 1), 0, actual[index], 0);
        }
    }

    private static StockTick tick(long[] bids, long[] bidVolumes,
                                  long[] asks, long[] askVolumes) {
        StockTick tick = new StockTick();
        System.arraycopy(bids, 0, tick.bp, 0, 5);
        System.arraycopy(bidVolumes, 0, tick.bv, 0, 5);
        System.arraycopy(asks, 0, tick.ap, 0, 5);
        System.arraycopy(askVolumes, 0, tick.av, 0, 5);
        return tick;
    }
}
