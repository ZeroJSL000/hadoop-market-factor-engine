package com.quant.factor.mr;

import com.quant.factor.logic.FactorCalculator;
import com.quant.factor.model.FactorResultWritable;
import com.quant.factor.model.StockTick;
import com.quant.factor.util.ProjectConstants;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Computes per-stock factors and aggregates by day and timestamp in mapper memory. */
public final class AlphaMapper extends Mapper<LongWritable, Text, Text, FactorResultWritable> {
    private final StockTick current = new StockTick();
    private final TickHistory history = new TickHistory();
    private final double[] factors = new double[ProjectConstants.FACTOR_COUNT];
    private final Text outputKey = new Text();
    private final FactorResultWritable outputValue = new FactorResultWritable();
    private final Map<String, Accumulator> localSums = new HashMap<>(32768);

    private static final class Accumulator {
        private final double[] sums = new double[ProjectConstants.FACTOR_COUNT];
        private int count;

        private void add(double[] values) {
            for (int index = 0; index < sums.length; index++) {
                sums[index] += values[index];
            }
            count++;
        }
    }

    @Override
    protected void map(LongWritable offset, Text line, Context context)
            throws IOException, InterruptedException {
        if (line.getLength() > 0 && line.getBytes()[0] == 't') {
            // Every source file has the same CSV header.
            return;
        }
        current.parse(line);
        if (!current.isValid()) {
            context.getCounter("Input", "Malformed rows").increment(1);
            return;
        }

        final long time;
        try {
            time = Long.parseLong(current.tradeTime);
        } catch (NumberFormatException exception) {
            context.getCounter("Input", "Malformed rows").increment(1);
            return;
        }
        if (time > 150000) {
            return;
        }

        StockTick previous = history.previousFor(current);
        if (previous != null && Long.parseLong(previous.tradeTime) >= time) {
            context.getCounter("Input", "Out-of-order snapshots").increment(1);
            return;
        }
        FactorCalculator.calculate(current, previous, factors);
        history.remember(current);

        // Pre-opening snapshots initialize history but do not enter the output.
        if (time >= 93000) {
            String key = current.tradingDay + '\t' + current.tradeTime;
            Accumulator accumulator = localSums.get(key);
            if (accumulator == null) {
                accumulator = new Accumulator();
                localSums.put(key, accumulator);
            }
            accumulator.add(factors);
        }
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        for (Map.Entry<String, Accumulator> entry : localSums.entrySet()) {
            outputKey.set(entry.getKey());
            Accumulator accumulator = entry.getValue();
            outputValue.set(accumulator.sums, accumulator.count);
            context.write(outputKey, outputValue);
        }
        localSums.clear();
    }
}
