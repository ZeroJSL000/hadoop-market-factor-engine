package com.quant.factor.mr;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Appends a finite number with exactly six decimal places. */
public final class FixedSixFormatter {
    private static final long SCALE = 1_000_000L;

    private FixedSixFormatter() {
    }

    public static void append(StringBuilder output, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Factor value must be finite");
        }
        double magnitude = Math.abs(value);
        if (magnitude > Long.MAX_VALUE / (double) SCALE) {
            output.append(BigDecimal.valueOf(value)
                    .setScale(6, RoundingMode.HALF_UP).toPlainString());
            return;
        }

        long scaled = Math.round(magnitude * SCALE);
        if (value < 0 && scaled != 0) {
            output.append('-');
        }
        output.append(scaled / SCALE).append('.');
        long fraction = scaled % SCALE;
        for (long place = SCALE / 10; place > 1 && fraction < place; place /= 10) {
            output.append('0');
        }
        output.append(fraction);
    }
}
