package com.quant.factor.logic;

import com.quant.factor.model.StockTick;
import com.quant.factor.util.ProjectConstants;

/** Computes the twenty five-level order-book formulas in course order. */
public final class FactorCalculator {
    private FactorCalculator() {
    }

    public static void calculate(StockTick current, StockTick previous, double[] factors) {
        if (factors.length < ProjectConstants.FACTOR_COUNT) {
            throw new IllegalArgumentException("Expected room for twenty factors");
        }

        double bestBid = current.bp[0];
        double bestAsk = current.ap[0];
        double bestBidVolume = current.bv[0];
        double bestAskVolume = current.av[0];
        double spread = bestAsk - bestBid;
        double midpoint = (bestAsk + bestBid) / 2.0;

        factors[0] = spread;
        factors[1] = safeDivide(spread, midpoint);
        factors[2] = midpoint;
        factors[3] = safeDivide(bestBidVolume - bestAskVolume,
                bestBidVolume + bestAskVolume);

        double bidDepth = 0;
        double askDepth = 0;
        double weightedBidSum = 0;
        double weightedAskSum = 0;
        double decayedBidDepth = 0;
        double decayedAskDepth = 0;
        for (int level = 0; level < ProjectConstants.LEVEL_COUNT; level++) {
            bidDepth += current.bv[level];
            askDepth += current.av[level];
            weightedBidSum += (double) current.bp[level] * current.bv[level];
            weightedAskSum += (double) current.ap[level] * current.av[level];
            decayedBidDepth += (double) current.bv[level] / (level + 1);
            decayedAskDepth += (double) current.av[level] / (level + 1);
        }

        factors[4] = safeDivide(bidDepth - askDepth, bidDepth + askDepth);
        factors[5] = bidDepth;
        factors[6] = askDepth;
        factors[7] = bidDepth - askDepth;
        factors[8] = safeDivide(bidDepth, askDepth);
        factors[9] = safeDivide((double) current.tBidVol - current.tAskVol,
                (double) current.tBidVol + current.tAskVol);

        double weightedBid = safeDivide(weightedBidSum, bidDepth);
        double weightedAsk = safeDivide(weightedAskSum, askDepth);
        factors[10] = weightedBid;
        factors[11] = weightedAsk;
        factors[12] = safeDivide(weightedBidSum + weightedAskSum,
                bidDepth + askDepth);
        factors[13] = weightedAsk - weightedBid;
        factors[14] = (bidDepth - askDepth) / ProjectConstants.LEVEL_COUNT;
        factors[15] = safeDivide(decayedBidDepth - decayedAskDepth,
                decayedBidDepth + decayedAskDepth);

        if (previous == null) {
            factors[16] = 0;
            factors[17] = 0;
            factors[18] = 0;
        } else {
            double previousMidpoint = ((double) previous.ap[0] + previous.bp[0]) / 2.0;
            double previousBidDepth = 0;
            double previousAskDepth = 0;
            for (int level = 0; level < ProjectConstants.LEVEL_COUNT; level++) {
                previousBidDepth += previous.bv[level];
                previousAskDepth += previous.av[level];
            }
            factors[16] = bestAsk - previous.ap[0];
            factors[17] = midpoint - previousMidpoint;
            factors[18] = safeDivide(bidDepth, askDepth)
                    - safeDivide(previousBidDepth, previousAskDepth);
        }

        factors[19] = safeDivide(spread, bidDepth + askDepth);
    }

    private static double safeDivide(double numerator, double denominator) {
        return numerator / (denominator == 0.0 ? ProjectConstants.EPSILON : denominator);
    }
}
