package dev.ignition.analytics.gateway.anomaly;

import dev.ignition.analytics.gateway.stats.StatisticsEngine;
import dev.ignition.analytics.gateway.timeseries.TimeSeriesAnalyzer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

import java.util.Arrays;

/**
 * Anomaly detection algorithms for numeric time series columns.
 *
 * Each method returns a copy of the table with an added boolean column named
 * "{column}_anomaly" (true = anomalous point).
 */
public final class AnomalyDetector {

    private AnomalyDetector() {}

    // -------------------------------------------------------------------------
    // Z-score
    // -------------------------------------------------------------------------

    /**
     * Flag values more than {@code threshold} standard deviations from the mean.
     *
     * @param threshold typical values: 2.0 (sensitive), 3.0 (standard), 4.0 (conservative)
     */
    public static Table zScore(Table table, String column, double threshold) {
        Table result = table.copy();
        double[] values = StatisticsEngine.extractDoubles(table, column);
        DescriptiveStatistics ds = new DescriptiveStatistics(values);
        double mean = ds.getMean();
        double std = ds.getStandardDeviation();

        BooleanColumn flag = BooleanColumn.create(column + "_anomaly", result.rowCount());

        if (std == 0) {
            result.addColumns(flag);
            return result;
        }

        DoubleColumn src = TimeSeriesAnalyzer.toDoubleColumn(result.column(column));
        for (int i = 0; i < src.size(); i++) {
            if (src.isMissing(i)) {
                flag.setMissing(i);
            } else {
                flag.set(i, Math.abs((src.get(i) - mean) / std) > threshold);
            }
        }

        result.addColumns(flag);
        return result;
    }

    // -------------------------------------------------------------------------
    // IQR (Interquartile Range)
    // -------------------------------------------------------------------------

    /**
     * Flag values outside [Q1 - fence*IQR, Q3 + fence*IQR].
     * Standard Tukey fence: 1.5 (mild outliers), 3.0 (extreme outliers).
     *
     * @param fence IQR multiplier (1.5 = mild outliers, 3.0 = extreme outliers)
     */
    public static Table iqr(Table table, String column, double fence) {
        Table result = table.copy();
        double[] sorted = StatisticsEngine.extractDoubles(table, column);
        Arrays.sort(sorted);

        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqrVal = q3 - q1;
        double lower = q1 - fence * iqrVal;
        double upper = q3 + fence * iqrVal;

        DoubleColumn src = TimeSeriesAnalyzer.toDoubleColumn(result.column(column));
        BooleanColumn flag = BooleanColumn.create(column + "_anomaly", src.size());

        for (int i = 0; i < src.size(); i++) {
            if (src.isMissing(i)) {
                flag.setMissing(i);
            } else {
                double v = src.get(i);
                flag.set(i, v < lower || v > upper);
            }
        }

        result.addColumns(flag);
        return result;
    }

    // -------------------------------------------------------------------------
    // EWMA (Exponentially Weighted Moving Average control chart)
    // -------------------------------------------------------------------------

    /**
     * Flag points where the EWMA residual exceeds {@code threshold} standard deviations.
     * Suitable for detecting process shifts that z-score misses because they drift slowly.
     *
     * @param lambda    smoothing factor in (0, 1]: lower = smoother, more sensitive to drift
     * @param threshold control limit in sigma units (typical: 3.0)
     */
    public static Table ewma(Table table, String column, double lambda, double threshold) {
        Table result = table.copy();
        DoubleColumn src = TimeSeriesAnalyzer.toDoubleColumn(result.column(column));

        double[] vals = StatisticsEngine.extractDoubles(table, column);
        DescriptiveStatistics ds = new DescriptiveStatistics(vals);
        double sigma = ds.getStandardDeviation();

        double ewmaStd = sigma * Math.sqrt(lambda / (2.0 - lambda));
        double limit = threshold * ewmaStd;

        BooleanColumn flag = BooleanColumn.create(column + "_anomaly", src.size());
        double ewmaVal = ds.getMean();

        for (int i = 0; i < src.size(); i++) {
            if (src.isMissing(i)) {
                flag.setMissing(i);
                continue;
            }
            double x = src.get(i);
            ewmaVal = lambda * x + (1.0 - lambda) * ewmaVal;
            flag.set(i, Math.abs(ewmaVal - ds.getMean()) > limit);
        }

        result.addColumns(flag);
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return Double.NaN;
        double rank = (p / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted[lo];
        return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
    }
}
