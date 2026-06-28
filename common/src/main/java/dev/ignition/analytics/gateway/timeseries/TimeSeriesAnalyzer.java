package dev.ignition.analytics.gateway.timeseries;

import dev.ignition.analytics.gateway.util.DatasetConverter;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Time series operations on Tablesaw Tables.
 *
 * All methods assume a DateTimeColumn exists (auto-detected) as the time axis.
 * Ignition tag history uses "t_stamp" as the first column by convention.
 */
public final class TimeSeriesAnalyzer {

    private TimeSeriesAnalyzer() {}

    // -------------------------------------------------------------------------
    // Resample
    // -------------------------------------------------------------------------

    /**
     * Downsample the table by truncating timestamps to a fixed interval and
     * aggregating numeric columns.
     *
     * @param table       source table
     * @param intervalStr interval string: "1m", "5m", "15m", "30m", "1h", "4h", "1d"
     * @param aggFunc     aggregation: "mean", "min", "max", "sum", "last", "first"
     */
    public static Table resample(Table table, String intervalStr, String aggFunc) {
        String tsCol = requireTimestampColumn(table);
        DateTimeColumn timestamps = (DateTimeColumn) table.column(tsCol);
        List<String> valueCols = DatasetConverter.numericColumnNames(table, tsCol);

        if (valueCols.isEmpty()) {
            throw new IllegalArgumentException("No numeric columns found to resample");
        }

        long intervalMinutes = parseIntervalMinutes(intervalStr);

        // Bucket each row to its interval start
        Map<LocalDateTime, List<Integer>> buckets = new LinkedHashMap<>();
        for (int r = 0; r < table.rowCount(); r++) {
            if (timestamps.isMissing(r)) {
                continue;
            }
            LocalDateTime bucket = truncate(timestamps.get(r), intervalMinutes);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(r);
        }

        // Build output columns
        DateTimeColumn outTime = DateTimeColumn.create(tsCol, buckets.size());
        Map<String, DoubleColumn> outValueCols = new LinkedHashMap<>();
        for (String vc : valueCols) {
            outValueCols.put(vc, DoubleColumn.create(vc, buckets.size()));
        }

        int bucketIdx = 0;
        for (Map.Entry<LocalDateTime, List<Integer>> entry : buckets.entrySet()) {
            outTime.set(bucketIdx, entry.getKey());

            for (String vc : valueCols) {
                DoubleColumn srcCol = toDoubleColumn(table.column(vc));
                List<Integer> rows = entry.getValue();
                double aggregated = aggregate(srcCol, rows, aggFunc);
                outValueCols.get(vc).set(bucketIdx, aggregated);
            }
            bucketIdx++;
        }

        List<Column<?>> outColumns = new ArrayList<>();
        outColumns.add(outTime);
        outColumns.addAll(outValueCols.values());
        return Table.create(table.name(), outColumns);
    }

    // -------------------------------------------------------------------------
    // Interpolate
    // -------------------------------------------------------------------------

    /**
     * Interpolate missing values in numeric columns.
     *
     * @param table  source table
     * @param method "linear", "forward" (forward fill), or "backward" (backward fill)
     */
    public static Table interpolate(Table table, String method) {
        Table result = table.copy();
        for (Column<?> col : result.columns()) {
            if (col instanceof DoubleColumn) {
                interpolateDoubleColumn((DoubleColumn) col, method);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Rolling statistics
    // -------------------------------------------------------------------------

    public static Table rollingMean(Table table, String column, int window) {
        return addRollingColumn(table, column, window, "mean", column + "_rollMean");
    }

    public static Table rollingStd(Table table, String column, int window) {
        return addRollingColumn(table, column, window, "std", column + "_rollStd");
    }

    public static Table rollingMin(Table table, String column, int window) {
        return addRollingColumn(table, column, window, "min", column + "_rollMin");
    }

    public static Table rollingMax(Table table, String column, int window) {
        return addRollingColumn(table, column, window, "max", column + "_rollMax");
    }

    // -------------------------------------------------------------------------
    // Null handling
    // -------------------------------------------------------------------------

    /** Drop all rows where any numeric column is missing. */
    public static Table dropNulls(Table table) {
        List<Integer> keepRows = new ArrayList<>();
        for (int r = 0; r < table.rowCount(); r++) {
            boolean hasMissing = false;
            for (Column<?> col : table.columns()) {
                if (col instanceof DoubleColumn && ((DoubleColumn) col).isMissing(r)) {
                    hasMissing = true;
                    break;
                }
            }
            if (!hasMissing) {
                keepRows.add(r);
            }
        }
        int[] arr = keepRows.stream().mapToInt(Integer::intValue).toArray();
        return table.rows(arr);
    }

    /** Fill missing values in a column with a constant. */
    public static Table fillNulls(Table table, String column, double fillValue) {
        Table result = table.copy();
        DoubleColumn col = result.doubleColumn(column);
        for (int r = 0; r < col.size(); r++) {
            if (col.isMissing(r)) {
                col.set(r, fillValue);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String requireTimestampColumn(Table table) {
        return DatasetConverter.findTimestampColumn(table)
            .orElseThrow(() -> new IllegalArgumentException(
                "No timestamp (Date) column found. Ignition tag history should have a 't_stamp' column."
            ));
    }

    private static long parseIntervalMinutes(String interval) {
        if (interval == null || interval.isBlank()) {
            throw new IllegalArgumentException("Interval must not be blank");
        }
        String s = interval.trim().toLowerCase();
        if (s.endsWith("d")) return Long.parseLong(s.replace("d", "")) * 1440;
        if (s.endsWith("h")) return Long.parseLong(s.replace("h", "")) * 60;
        if (s.endsWith("m")) return Long.parseLong(s.replace("m", ""));
        throw new IllegalArgumentException("Unrecognised interval: '" + interval + "'. Use e.g. '5m', '1h', '1d'.");
    }

    private static LocalDateTime truncate(LocalDateTime dt, long intervalMinutes) {
        long totalMinutes = dt.getHour() * 60L + dt.getMinute();
        long bucketStart = (totalMinutes / intervalMinutes) * intervalMinutes;
        return dt.toLocalDate()
            .atStartOfDay()
            .plusMinutes(bucketStart);
    }

    private static double aggregate(DoubleColumn col, List<Integer> rows, String func) {
        switch (func.toLowerCase()) {
            case "min": {
                OptionalDouble opt = rows.stream()
                    .filter(r -> !col.isMissing(r))
                    .mapToDouble(col::get).min();
                return opt.isPresent() ? opt.getAsDouble() : Double.NaN;
            }
            case "max": {
                OptionalDouble opt = rows.stream()
                    .filter(r -> !col.isMissing(r))
                    .mapToDouble(col::get).max();
                return opt.isPresent() ? opt.getAsDouble() : Double.NaN;
            }
            case "sum":
                return rows.stream().filter(r -> !col.isMissing(r)).mapToDouble(col::get).sum();
            case "last": {
                int last = rows.get(rows.size() - 1);
                return col.isMissing(last) ? Double.NaN : col.get(last);
            }
            case "first": {
                int first = rows.get(0);
                return col.isMissing(first) ? Double.NaN : col.get(first);
            }
            default: { // "mean"
                OptionalDouble opt = rows.stream()
                    .filter(r -> !col.isMissing(r))
                    .mapToDouble(col::get).average();
                return opt.isPresent() ? opt.getAsDouble() : Double.NaN;
            }
        }
    }

    public static DoubleColumn toDoubleColumn(Column<?> col) {
        if (col instanceof DoubleColumn) {
            return (DoubleColumn) col;
        }
        if (col instanceof LongColumn) {
            LongColumn lc = (LongColumn) col;
            DoubleColumn dc = DoubleColumn.create(col.name(), col.size());
            for (int i = 0; i < col.size(); i++) {
                if (lc.isMissing(i)) dc.setMissing(i);
                else dc.set(i, (double) lc.get(i));
            }
            return dc;
        }
        if (col instanceof IntColumn) {
            IntColumn ic = (IntColumn) col;
            DoubleColumn dc = DoubleColumn.create(col.name(), col.size());
            for (int i = 0; i < col.size(); i++) {
                if (ic.isMissing(i)) dc.setMissing(i);
                else dc.set(i, (double) ic.get(i));
            }
            return dc;
        }
        throw new IllegalArgumentException("Column '" + col.name() + "' is not numeric");
    }

    private static void interpolateDoubleColumn(DoubleColumn col, String method) {
        int n = col.size();
        if ("forward".equals(method)) {
            double last = Double.NaN;
            for (int i = 0; i < n; i++) {
                if (!col.isMissing(i)) {
                    last = col.get(i);
                } else if (!Double.isNaN(last)) {
                    col.set(i, last);
                }
            }
        } else if ("backward".equals(method)) {
            double next = Double.NaN;
            for (int i = n - 1; i >= 0; i--) {
                if (!col.isMissing(i)) {
                    next = col.get(i);
                } else if (!Double.isNaN(next)) {
                    col.set(i, next);
                }
            }
        } else { // "linear"
            int start = -1;
            for (int i = 0; i < n; i++) {
                if (!col.isMissing(i)) {
                    if (start >= 0 && i - start > 1) {
                        double v0 = col.get(start);
                        double v1 = col.get(i);
                        for (int j = start + 1; j < i; j++) {
                            double frac = (double) (j - start) / (i - start);
                            col.set(j, v0 + frac * (v1 - v0));
                        }
                    }
                    start = i;
                }
            }
        }
    }

    private static Table addRollingColumn(Table table, String column, int window, String func, String newColName) {
        Table result = table.copy();
        DoubleColumn src = toDoubleColumn(result.column(column));
        DoubleColumn out = DoubleColumn.create(newColName, src.size());

        for (int i = 0; i < src.size(); i++) {
            if (i < window - 1) {
                out.setMissing(i);
                continue;
            }
            double[] vals = new double[window];
            int count = 0;
            for (int j = i - window + 1; j <= i; j++) {
                if (!src.isMissing(j)) {
                    vals[count++] = src.get(j);
                }
            }
            if (count == 0) {
                out.setMissing(i);
                continue;
            }
            double[] trimmed = Arrays.copyOf(vals, count);
            double v;
            switch (func) {
                case "min": v = Arrays.stream(trimmed).min().getAsDouble(); break;
                case "max": v = Arrays.stream(trimmed).max().getAsDouble(); break;
                case "std": v = std(trimmed); break;
                default:    v = Arrays.stream(trimmed).average().getAsDouble(); break;
            }
            out.set(i, v);
        }

        result.addColumns(out);
        return result;
    }

    private static double std(double[] vals) {
        if (vals.length < 2) return 0.0;
        double mean = Arrays.stream(vals).average().orElse(0);
        double variance = Arrays.stream(vals).map(v -> (v - mean) * (v - mean)).sum() / (vals.length - 1);
        return Math.sqrt(variance);
    }
}
