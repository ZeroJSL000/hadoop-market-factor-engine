# Hadoop Market Factor Engine

This repository presents the optimized Java/Hadoop implementation from a two-person Fall 2025 course project at Southern University of Science and Technology. It computes 20 order-book factors from high-frequency CSI 300 stock snapshots and produces a cross-sectional mean for each trading timestamp. The public code is organized for independent review; the course data and reference answers are not redistributed.

## What the pipeline does

1. `StockTick` scans Hadoop `Text` bytes for the fields used by the formulas. The source files contain ten bid/ask levels; the specified formulas use the first five. Prices remain in the input's integer units.
2. `FactorCalculator` evaluates the 20 course formulas: spread, midpoint, depth and imbalance, volume-weighted prices, level-decayed imbalance, three changes from the previous snapshot, and a spread-to-depth measure.
3. `AlphaMapper` caches the previous snapshot by **trading day and stock**, calculates factors, and accumulates a 20-value sum and row count for each `(day, time)` key in mapper memory. Pre-09:30 snapshots warm the cache without entering the output.
4. `DayPartitioner` sends all timestamps of a trading day to one reducer. `AlphaReducer` merges mapper sums and counts, then divides by the observed row count at that timestamp. There is no separately configured Hadoop combiner in this optimized version.
5. `Driver` adds the CSV header and finalizes one `YYYYMMDD.csv` file per day. It refuses to overwrite an existing output directory.

The job uses `CombineTextInputFormat` with a 256 MiB maximum split, a 4 MiB file-system buffer, and optional Snappy compression of mapper output (on by default). Mapper aggregation is the principal shuffle-volume reduction. The byte scanner still creates strings for identifiers and snapshot copies for temporal factors, so it is an allocation-reduction strategy, not a zero-allocation parser.

## Input and output

Input is the course snapshot CSV schema: `tradingDay,tradeTime,recvTime,MIC,code,...,tBidVol,tAskVol,...,bp1,bv1,ap1,av1,...`. The parser expects comma-separated rows without quoted commas and integer-valued price/volume columns. Each stock/day file should be in timestamp order and small enough to remain within one input split. The three temporal factors (`alpha_17`–`alpha_19`) depend on this condition. A stock/day stream split between mappers would need an explicit boundary-state strategy.

The output has `tradeTime,alpha_1,...,alpha_20`, followed by six-decimal rows from 09:30:00 through 15:00:00 when those timestamps are present. Output names use the full date, for example `20240102.csv`. Archived course submissions and provided answers used month-day names such as `0102.csv`; the comparison script accepts either convention. Averaging uses the number of observed snapshots at each timestamp, so incomplete timestamps are not silently divided by a fixed 300.

## Build and run

Requirements: JDK 8 or newer, Maven, and a Hadoop 3.3.4 environment for running the job. Hadoop dependencies are marked `provided` because `hadoop jar` supplies them on a cluster.

```bash
mvn package
hadoop jar target/hadoop-market-factor-engine-1.0.0.jar \
  com.quant.factor.Driver \
  /user/me/snapshots \
  /user/me/factor-output
hadoop fs -cat /user/me/factor-output/20240102.csv | head
```

The input path may also use a Hadoop-supported `file:///` URI for a local-file-system run. The output directory must not exist before the job starts. For environments without native Snappy support, add `-Dfactor.shuffle.snappy=false` before the two paths. Reducer count can be set with `-Dmapreduce.job.reduces=2`; the date partitioner still keeps each day together.

The input directory should contain only snapshot CSV files in the documented schema. The public repository contains no market data. Unpack legally available input data outside the repository and point the job to that directory or upload it to HDFS.

## Verify factor outputs

The included checker requires the same day and timestamp set in actual and reference CSVs and checks for duplicate or non-finite rows. For a nonzero reference, it computes `abs(actual - reference) / abs(reference)` and pools the mean error over all selected timestamps. A zero reference paired with zero actual has zero error; a zero reference paired with a nonzero actual fails. The default pass threshold is 1% per factor.

```bash
python scripts/verify_outputs.py \
  --reference-dir /path/to/private-reference \
  --actual-dir /path/to/factor-output
python -m unittest discover -s scripts -p 'test_*.py'
```

Five-day **archived** output files from the course project were independently compared with the provided reference files: all 20 factors were below the 1% mean-error threshold over 4,802 matching timestamps per day. The largest pooled mean error was approximately 0.02615% (`alpha_2`). These archived files were not regenerated from this cleaned repository, so that result is historical evidence rather than a validation run of the published code.

The course presentation reported a 102-second baseline and an approximately 11-second optimized run; an archived screenshot shows `11.049` seconds. These were measurements in the course environment, not a benchmark reproduced on this repository. This cleanup also omits some historical environment-specific tuning settings, so the reported speedup should not be extrapolated to new data or hardware.

## Repository scope

The repository includes the optimized mapper-side aggregation, byte-array scanning, fast fixed-decimal formatting, the 20 formulas, build configuration, and synthetic tests. Raw snapshots, reference answers, generated results, compiled artifacts, course handouts, and the presentation are intentionally excluded. The tests check parser behavior, formula values, temporal isolation, serialization, partitioning, formatting, and the CSV comparison command; they do not substitute for a full HDFS/MapReduce integration run.
