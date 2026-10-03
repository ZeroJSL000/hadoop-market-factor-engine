package com.quant.factor;

import com.quant.factor.model.FactorResultWritable;
import com.quant.factor.model.StockTick;
import com.quant.factor.mr.DayPartitioner;
import com.quant.factor.mr.FixedSixFormatter;
import com.quant.factor.mr.TickHistory;
import org.apache.hadoop.io.Text;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PipelinePrimitivesTest {
    @Test
    public void keepsTemporalStateWithinOneStockAndTradingDay() {
        TickHistory history = new TickHistory();
        StockTick first = tick("20240102", "000001", "092500", 100);
        assertNull(history.previousFor(first));
        history.remember(first);
        first.ap[0] = 999;
        assertEquals(100, history.previousFor(first).ap[0]);
        assertNull(history.previousFor(tick("20240103", "000001", "092500", 101)));
        assertNull(history.previousFor(tick("20240102", "000002", "092500", 101)));
    }

    @Test
    public void partitionsAllTimesOfOneDayTogether() {
        DayPartitioner partitioner = new DayPartitioner();
        FactorResultWritable value = new FactorResultWritable();
        assertEquals(partitioner.getPartition(new Text("20240102\t093000"), value, 7),
                partitioner.getPartition(new Text("20240102\t145700"), value, 7));
    }

    @Test
    public void preservesCountAndTwentySumsThroughHadoopSerialization() throws Exception {
        double[] sums = new double[20];
        for (int index = 0; index < sums.length; index++) {
            sums[index] = index + 0.25;
        }
        FactorResultWritable original = new FactorResultWritable();
        original.set(sums, 300);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.write(new DataOutputStream(bytes));
        FactorResultWritable restored = new FactorResultWritable();
        restored.readFields(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(300, restored.getCount());
        for (int index = 0; index < sums.length; index++) {
            assertEquals(sums[index], restored.getSums()[index], 0);
        }
    }

    @Test
    public void formatsSixDecimalsWithCarryAndSign() {
        assertFormat(1.9999996, "2.000000");
        assertFormat(-2.1234564, "-2.123456");
        assertFormat(-0.0000004, "0.000000");
        assertFormat(12.34, "12.340000");
    }

    private static void assertFormat(double value, String expected) {
        StringBuilder output = new StringBuilder();
        FixedSixFormatter.append(output, value);
        assertEquals(expected, output.toString());
    }

    private static StockTick tick(String day, String code, String time, long ask) {
        StockTick tick = new StockTick();
        tick.tradingDay = day;
        tick.code = code;
        tick.tradeTime = time;
        tick.ap[0] = ask;
        return tick;
    }
}
