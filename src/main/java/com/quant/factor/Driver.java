package com.quant.factor;

import com.quant.factor.model.FactorResultWritable;
import com.quant.factor.mr.AlphaMapper;
import com.quant.factor.mr.AlphaReducer;
import com.quant.factor.mr.DayPartitioner;
import com.quant.factor.util.ProjectConstants;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.SnappyCodec;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.CombineTextInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Configures the optimized MapReduce pipeline and finalizes daily CSV files. */
public final class Driver extends Configured implements Tool {
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final long MAX_SPLIT_SIZE = 256L * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new Configuration(), new Driver(), args));
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: hadoop jar <jar> <input path> <output path>");
            return 2;
        }

        Configuration configuration = getConf();
        Path input = new Path(args[0]);
        Path output = new Path(args[1]);
        if (!input.getFileSystem(configuration).exists(input)) {
            System.err.println("Input path does not exist: " + input);
            return 2;
        }
        FileSystem outputFileSystem = output.getFileSystem(configuration);
        if (outputFileSystem.exists(output)) {
            System.err.println("Output path already exists: " + output);
            return 2;
        }
        if (configuration.getInt("mapreduce.job.reduces", 1) < 1) {
            System.err.println("At least one reducer is required.");
            return 2;
        }

        long start = System.nanoTime();
        Job job = Job.getInstance(configuration, "Twenty Order-Book Factors");
        job.setJarByClass(Driver.class);
        job.getConfiguration().setInt("io.file.buffer.size", BUFFER_SIZE);
        if (job.getConfiguration().getBoolean("factor.shuffle.snappy", true)) {
            job.getConfiguration().setBoolean("mapreduce.map.output.compress", true);
            job.getConfiguration().setClass("mapreduce.map.output.compress.codec",
                    SnappyCodec.class, CompressionCodec.class);
        }

        job.setMapperClass(AlphaMapper.class);
        job.setReducerClass(AlphaReducer.class);
        job.setPartitionerClass(DayPartitioner.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(FactorResultWritable.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(CombineTextInputFormat.class);
        CombineTextInputFormat.setMaxInputSplitSize(job, MAX_SPLIT_SIZE);
        FileInputFormat.setInputDirRecursive(job, true);
        LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);

        if (!job.waitForCompletion(true)) {
            return 1;
        }
        finalizeCsvFiles(outputFileSystem, output);
        System.out.printf("Completed in %.3f seconds%n", (System.nanoTime() - start) / 1e9);
        return 0;
    }

    private static void finalizeCsvFiles(FileSystem fileSystem, Path directory)
            throws IOException {
        Map<String, Path> dailyOutputs = new LinkedHashMap<>();
        for (FileStatus status : fileSystem.listStatus(directory)) {
            if (!status.isFile()) {
                continue;
            }
            String name = status.getPath().getName();
            if (!name.matches("[0-9]{8}-r-[0-9]{5}")) {
                continue;
            }
            String day = name.substring(0, 8);
            if (dailyOutputs.put(day, status.getPath()) != null) {
                throw new IOException("Multiple reducer files for " + day);
            }
        }
        if (dailyOutputs.isEmpty()) {
            throw new IOException("The job produced no daily factor rows");
        }

        for (Map.Entry<String, Path> entry : dailyOutputs.entrySet()) {
            Path finalPath = new Path(directory, entry.getKey() + ".csv");
            Path temporaryPath = new Path(directory, "_" + entry.getKey() + ".csv.tmp");
            if (fileSystem.exists(finalPath) || fileSystem.exists(temporaryPath)) {
                throw new IOException("Daily CSV destination already exists: " + finalPath);
            }
            try (FSDataOutputStream writer = fileSystem.create(temporaryPath, false);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         fileSystem.open(entry.getValue()), StandardCharsets.UTF_8), BUFFER_SIZE)) {
                writer.write(csvHeader().getBytes(StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line.getBytes(StandardCharsets.UTF_8));
                    writer.write('\n');
                }
            }
            if (!fileSystem.rename(temporaryPath, finalPath)) {
                throw new IOException("Could not rename " + temporaryPath + " to " + finalPath);
            }
            if (!fileSystem.delete(entry.getValue(), false)) {
                throw new IOException("Could not remove raw reducer file: " + entry.getValue());
            }
            System.out.println("Generated " + finalPath);
        }
    }

    private static String csvHeader() {
        StringBuilder header = new StringBuilder("tradeTime");
        for (int index = 1; index <= ProjectConstants.FACTOR_COUNT; index++) {
            header.append(",alpha_").append(index);
        }
        return header.append('\n').toString();
    }
}
