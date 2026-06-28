package dev.ignition.analytics.gateway.stats;

import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.Dataset;
import dev.ignition.analytics.gateway.util.DatasetConverter;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

import java.util.*;

/**
 * Descriptive statistics and correlation analysis.
 */
public final class StatisticsEngine {

    private StatisticsEngine() {}

    // -------------------------------------------------------------------------
    // Describe
    // -------------------------------------------------------------------------

    /**
     * Compute descriptive statistics for all numeric columns.
     * Returns a Dataset with rows: count, mean, std, min, 25%, 50%, 75%, max.
     */
    public static Dataset describe(Table table) {
        List<String> numericCols = DatasetConverter.numericColumnNames(table,
            DatasetConverter.findTimestampColumn(table).orElse(""));

        String[] statNames = {"count", "mean", "std", "min", "25%", "50%", "75%", "max"};
        int numStats = statNames.length;
        int numCols = numericCols.size();

        String[] colNames = new String[numCols + 1];
        Class<?>[] colTypes = new Class<?>[numCols + 1];
        Object[][] data = new Object[numStats][numCols + 1];

        colNames[0] = "stat";
        colTypes[0] = String.class;
        for (int i = 0; i < numStats; i++) {
            data[i][0] = statNames[i];
        }

        for (int ci = 0; ci < numCols; ci++) {
            String colName = numericCols.get(ci);
            colNames[ci + 1] = colName;
            colTypes[ci + 1] = Double.class;

            double[] values = extractDoubles(table, colName);
            DescriptiveStatistics ds = new DescriptiveStatistics(values);

            double[] sorted = values.clone();
            Arrays.sort(sorted);

            data[0][ci + 1] = (double) values.length;
            data[1][ci + 1] = ds.getMean();
            data[2][ci + 1] = ds.getStandardDeviation();
            data[3][ci + 1] = ds.getMin();
            data[4][ci + 1] = percentile(sorted, 25);
            data[5][ci + 1] = percentile(sorted, 50);
            data[6][ci + 1] = percentile(sorted, 75);
            data[7][ci + 1] = ds.getMax();
        }

        return new BasicDataset(colNames, colTypes, data);
    }

    // -------------------------------------------------------------------------
    // Correlation
    // -------------------------------------------------------------------------

    /**
     * Compute Pearson correlation matrix for all numeric columns.
     * Returns a Dataset where the first column is the column name and
     * subsequent columns contain correlation coefficients.
     */
    public static Dataset correlate(Table table) {
        List<String> numericCols = DatasetConverter.numericColumnNames(table,
            DatasetConverter.findTimestampColumn(table).orElse(""));

        if (numericCols.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 numeric columns for correlation");
        }

        int n = numericCols.size();

        List<double[]> arrays = new ArrayList<>();
        for (String col : numericCols) {
            arrays.add(extractDoubles(table, col));
        }

        double[][] corrMatrix = new double[n][n];
        PearsonsCorrelation pc = new PearsonsCorrelation();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    corrMatrix[i][j] = 1.0;
                } else {
                    double[] a = alignedFirst(arrays.get(i), arrays.get(j));
                    double[] b = alignedSecond(arrays.get(i), arrays.get(j));
                    corrMatrix[i][j] = pc.correlation(a, b);
                }
            }
        }

        String[] colNames = new String[n + 1];
        Class<?>[] colTypes = new Class<?>[n + 1];
        Object[][] data = new Object[n][n + 1];

        colNames[0] = "column";
        colTypes[0] = String.class;
        for (int j = 0; j < n; j++) {
            colNames[j + 1] = numericCols.get(j);
            colTypes[j + 1] = Double.class;
        }

        for (int i = 0; i < n; i++) {
            data[i][0] = numericCols.get(i);
            for (int j = 0; j < n; j++) {
                data[i][j + 1] = corrMatrix[i][j];
            }
        }

        return new BasicDataset(colNames, colTypes, data);
    }

    // -------------------------------------------------------------------------
    // Normalize
    // -------------------------------------------------------------------------

    /**
     * Normalize a column in-place on a copy of the table.
     *
     * @param method "minmax" scales to [0, 1]; "zscore" standardizes to mean=0 std=1
     */
    public static Table normalize(Table table, String column, String method) {
        Table result = table.copy();
        DoubleColumn col = result.doubleColumn(column);
        double[] vals = extractDoubles(result, column);

        DescriptiveStatistics stats = new DescriptiveStatistics(vals);

        if ("zscore".equals(method)) {
            double mean = stats.getMean();
            double std = stats.getStandardDeviation();
            if (std == 0) throw new IllegalArgumentException("Standard deviation is 0 — cannot z-score normalize");
            for (int i = 0; i < col.size(); i++) {
                if (!col.isMissing(i)) {
                    col.set(i, (col.get(i) - mean) / std);
                }
            }
        } else { // "minmax"
            double min = stats.getMin();
            double range = stats.getMax() - min;
            if (range == 0) throw new IllegalArgumentException("Range is 0 — cannot min-max normalize");
            for (int i = 0; i < col.size(); i++) {
                if (!col.isMissing(i)) {
                    col.set(i, (col.get(i) - min) / range);
                }
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Package-private helpers (used by AnomalyDetector)
    // -------------------------------------------------------------------------

    /** Extract non-missing values from a column as a primitive double array. */
    public static double[] extractDoubles(Table table, String columnName) {
        Column<?> col = table.column(columnName);
        DoubleColumn dc;
        if (col instanceof DoubleColumn) {
            dc = (DoubleColumn) col;
        } else if (col instanceof LongColumn) {
            dc = ((LongColumn) col).asDoubleColumn();
        } else if (col instanceof IntColumn) {
            dc = ((IntColumn) col).asDoubleColumn();
        } else {
            throw new IllegalArgumentException("Column '" + columnName + "' is not numeric");
        }

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < dc.size(); i++) {
            if (!dc.isMissing(i)) {
                result.add(dc.get(i));
            }
        }
        double[] arr = new double[result.size()];
        for (int i = 0; i < result.size(); i++) arr[i] = result.get(i);
        return arr;
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

    private static double[] alignedFirst(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            if (!Double.isNaN(a[i]) && !Double.isNaN(b[i])) out.add(a[i]);
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < out.size(); i++) arr[i] = out.get(i);
        return arr;
    }

    private static double[] alignedSecond(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            if (!Double.isNaN(a[i]) && !Double.isNaN(b[i])) out.add(b[i]);
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < out.size(); i++) arr[i] = out.get(i);
        return arr;
    }
}
