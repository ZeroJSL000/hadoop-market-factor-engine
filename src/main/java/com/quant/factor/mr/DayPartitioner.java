package com.quant.factor.mr;

import com.quant.factor.model.FactorResultWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;

/** Sends every timestamp of one trading day to the same reducer. */
public final class DayPartitioner extends Partitioner<Text, FactorResultWritable> {
    @Override
    public int getPartition(Text key, FactorResultWritable value, int partitions) {
        if (partitions < 1) {
            throw new IllegalArgumentException("At least one reducer is required");
        }
        String dateAndTime = key.toString();
        int separator = dateAndTime.indexOf('\t');
        if (separator != 8) {
            throw new IllegalArgumentException("Expected YYYYMMDD and trade time");
        }
        return (dateAndTime.substring(0, separator).hashCode() & Integer.MAX_VALUE)
                % partitions;
    }
}
