package com.quant.factor.model;

import com.quant.factor.util.ProjectConstants;
import org.apache.hadoop.io.Text;

import java.nio.charset.StandardCharsets;

/** A single stock's order-book snapshot in the source CSV's integer units. */
public final class StockTick {
    public String tradingDay;
    public String tradeTime;
    public String code;
    public final long[] bp = new long[ProjectConstants.LEVEL_COUNT];
    public final long[] bv = new long[ProjectConstants.LEVEL_COUNT];
    public final long[] ap = new long[ProjectConstants.LEVEL_COUNT];
    public final long[] av = new long[ProjectConstants.LEVEL_COUNT];
    public long tBidVol;
    public long tAskVol;
    private boolean valid;

    /** Parse only the fields needed by the formulas from Hadoop's Text buffer. */
    public void parse(Text text) {
        valid = false;
        tradingDay = null;
        tradeTime = null;
        code = null;
        if (text == null) {
            return;
        }

        byte[] bytes = text.getBytes();
        int length = text.getLength();
        int start = 0;
        int column = 0;
        try {
            for (int end = 0; end <= length; end++) {
                if (end != length && bytes[end] != ',') {
                    continue;
                }
                switch (column) {
                    case 0:
                        tradingDay = ascii(bytes, start, end);
                        break;
                    case 1:
                        tradeTime = ascii(bytes, start, end);
                        break;
                    case 4:
                        code = ascii(bytes, start, end);
                        break;
                    case 12:
                        tBidVol = parseLong(bytes, start, end);
                        break;
                    case 13:
                        tAskVol = parseLong(bytes, start, end);
                        break;
                    default:
                        break;
                }

                if (column >= 17 && column < 37) {
                    int relative = column - 17;
                    int level = relative / 4;
                    long value = parseLong(bytes, start, end);
                    switch (relative % 4) {
                        case 0:
                            bp[level] = value;
                            break;
                        case 1:
                            bv[level] = value;
                            break;
                        case 2:
                            ap[level] = value;
                            break;
                        default:
                            av[level] = value;
                            break;
                    }
                }

                if (column == 36) {
                    valid = tradingDay != null && tradingDay.length() == 8
                            && tradeTime != null && !tradeTime.isEmpty()
                            && code != null && !code.isEmpty();
                    return;
                }
                start = end + 1;
                column++;
            }
        } catch (NumberFormatException exception) {
            valid = false;
        }
    }

    public boolean isValid() {
        return valid;
    }

    /** Copy the fields needed by the three time-difference factors. */
    public StockTick copy() {
        StockTick result = new StockTick();
        result.tradingDay = tradingDay;
        result.tradeTime = tradeTime;
        result.code = code;
        System.arraycopy(bp, 0, result.bp, 0, ProjectConstants.LEVEL_COUNT);
        System.arraycopy(bv, 0, result.bv, 0, ProjectConstants.LEVEL_COUNT);
        System.arraycopy(ap, 0, result.ap, 0, ProjectConstants.LEVEL_COUNT);
        System.arraycopy(av, 0, result.av, 0, ProjectConstants.LEVEL_COUNT);
        result.tBidVol = tBidVol;
        result.tAskVol = tAskVol;
        result.valid = valid;
        return result;
    }

    private static String ascii(byte[] bytes, int start, int end) {
        return new String(bytes, start, end - start, StandardCharsets.US_ASCII);
    }

    /** The course snapshot schema stores price and volume columns as signed Int64. */
    private static long parseLong(byte[] bytes, int start, int end) {
        if (start == end) {
            throw new NumberFormatException("Empty numeric field");
        }
        boolean negative = bytes[start] == '-';
        int first = negative ? start + 1 : start;
        if (first == end) {
            throw new NumberFormatException("Sign without digits");
        }
        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long minimumBeforeMultiply = limit / 10;
        long result = 0;
        for (int index = first; index < end; index++) {
            int digit = bytes[index] - '0';
            if (digit < 0 || digit > 9 || result < minimumBeforeMultiply) {
                throw new NumberFormatException("Invalid Int64 field");
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException("Invalid Int64 field");
            }
            result -= digit;
        }
        return negative ? result : -result;
    }
}
