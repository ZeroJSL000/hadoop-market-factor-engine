package com.quant.factor.mr;

import com.quant.factor.model.FactorResultWritable;
import com.quant.factor.util.ProjectConstants;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;

import java.io.IOException;

/** Computes cross-sectional averages and writes one raw stream per trading day. */
public final class AlphaReducer
        extends Reducer<Text, FactorResultWritable, NullWritable, Text> {
    private MultipleOutputs<NullWritable, Text> outputs;
    private final StringBuilder row = new StringBuilder(512);
    private final Text outputValue = new Text();

    @Override
    protected void setup(Context context) {
        outputs = new MultipleOutputs<>(context);
    }

    @Override
    protected void reduce(Text key, Iterable<FactorResultWritable> values, Context context)
            throws IOException, InterruptedException {
        String dateAndTime = key.toString();
        int separator = dateAndTime.indexOf('\t');
        if (separator != 8) {
            throw new IOException("Unexpected aggregation key: " + dateAndTime);
        }
        String day = dateAndTime.substring(0, separator);
        String time = dateAndTime.substring(separator + 1);
        double[] sums = new double[ProjectConstants.FACTOR_COUNT];
        long count = 0;
        for (FactorResultWritable value : values) {
            double[] partialSums = value.getSums();
            for (int index = 0; index < sums.length; index++) {
                sums[index] += partialSums[index];
            }
            count += value.getCount();
        }
        if (count == 0) {
            return;
        }

        row.setLength(0);
        row.append(time);
        for (double sum : sums) {
            row.append(',');
            FixedSixFormatter.append(row, sum / count);
        }
        outputValue.set(row.toString());
        outputs.write(NullWritable.get(), outputValue, day);
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        outputs.close();
    }
}
