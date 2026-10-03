package com.quant.factor.model;

import com.quant.factor.util.ProjectConstants;
import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** An associative sum-and-count value for cross-sectional averaging. */
public final class FactorResultWritable implements Writable {
    private final double[] sums = new double[ProjectConstants.FACTOR_COUNT];
    private int count;

    public void set(double[] values, int valueCount) {
        System.arraycopy(values, 0, sums, 0, ProjectConstants.FACTOR_COUNT);
        count = valueCount;
    }

    public double[] getSums() {
        return sums;
    }

    public int getCount() {
        return count;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(count);
        for (double sum : sums) {
            output.writeDouble(sum);
        }
    }

    @Override
    public void readFields(DataInput input) throws IOException {
        count = input.readInt();
        for (int index = 0; index < sums.length; index++) {
            sums[index] = input.readDouble();
        }
    }
}
