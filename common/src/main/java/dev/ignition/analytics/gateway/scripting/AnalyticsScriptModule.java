package dev.ignition.analytics.gateway.scripting;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.script.hints.JythonElement;
import com.inductiveautomation.ignition.common.script.hints.ScriptArg;
import dev.ignition.analytics.gateway.anomaly.AnomalyDetector;
import dev.ignition.analytics.gateway.forecast.Forecaster;
import dev.ignition.analytics.gateway.ml.MLEngine;
import dev.ignition.analytics.gateway.stats.StatisticsEngine;
import dev.ignition.analytics.gateway.timeseries.TimeSeriesAnalyzer;
import dev.ignition.analytics.gateway.util.DatasetConverter;
import tech.tablesaw.api.Table;

import java.io.StringWriter;

/**
 * Scripting functions exposed as {@code system.analytics.*} in Ignition Jython.
 *
 * All methods accept and return standard Ignition {@link Dataset} objects so
 * they integrate directly with {@code system.tag.queryTagHistory()} output and
 * the rest of the Ignition scripting API.
 *
 * <h3>Typical workflow</h3>
 * <pre>
 *   tagData  = system.tag.queryTagHistory(
 *                  paths=["[default]Reactor/Temperature"],
 *                  startDate=system.date.addHours(system.date.now(), -24),
 *                  endDate=system.date.now(),
 *                  returnSize=0)
 *
 *   # Downsample to 1-hour means
 *   hourly   = system.analytics.resample(tagData, "1h", "mean")
 *
 *   # Fill any gaps with linear interpolation
 *   clean    = system.analytics.interpolate(hourly, "linear")
 *
 *   # Flag anomalies using z-score
 *   flagged  = system.analytics.detectAnomaliesZScore(clean, "Temperature", 3.0)
 *
 *   # 24-hour forecast
 *   forecast = system.analytics.forecast(clean, "Temperature", 24, "1h")
 * </pre>
 */
public class AnalyticsScriptModule {

    private static final String DOC_BUNDLE_PREFIX = "AnalyticsScriptModule";

    static {
        BundleUtil.get().addBundle(
            AnalyticsScriptModule.class.getSimpleName(),
            AnalyticsScriptModule.class.getClassLoader(),
            AnalyticsScriptModule.class.getName().replace('.', '/')
        );
    }

    // =========================================================================
    // Time series — resampling and interpolation
    // =========================================================================

    /**
     * Resample a tag history Dataset to a fixed time interval.
     *
     * @param dataset     Ignition Dataset (must contain a Date timestamp column, e.g. t_stamp)
     * @param intervalStr resampling interval: "1m", "5m", "15m", "30m", "1h", "4h", "1d"
     * @param aggregation aggregation function: "mean", "min", "max", "sum", "first", "last"
     * @return resampled Dataset at the new frequency
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset resample(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("intervalStr") String intervalStr,
        @ScriptArg("aggregation") String aggregation
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = TimeSeriesAnalyzer.resample(table, intervalStr, aggregation);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Interpolate missing values in all numeric columns.
     *
     * @param dataset Ignition Dataset
     * @param method  "linear" (default), "forward" (last-observation-carried-forward),
     *                or "backward"
     * @return Dataset with missing values filled
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset interpolate(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("method") String method
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = TimeSeriesAnalyzer.interpolate(table, method);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Drop all rows where any numeric column contains a missing value.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset dropNulls(@ScriptArg("dataset") Dataset dataset) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = TimeSeriesAnalyzer.dropNulls(table);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Replace missing values in a single column with a constant.
     *
     * @param dataset   Ignition Dataset
     * @param column    column name
     * @param fillValue replacement value
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset fillNulls(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("fillValue") double fillValue
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = TimeSeriesAnalyzer.fillNulls(table, column, fillValue);
        return DatasetConverter.toDataset(result);
    }

    // =========================================================================
    // Rolling / windowed statistics
    // =========================================================================

    /**
     * Add a rolling mean column to the Dataset.
     *
     * @param dataset Ignition Dataset
     * @param column  numeric column to compute over
     * @param window  window size in rows
     * @return original Dataset plus a new column named {@code column}_rollMean
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset rollingMean(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("window") int window
    ) {
        Table table = DatasetConverter.toTable(dataset);
        return DatasetConverter.toDataset(TimeSeriesAnalyzer.rollingMean(table, column, window));
    }

    /** Add a rolling standard deviation column. */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset rollingStd(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("window") int window
    ) {
        Table table = DatasetConverter.toTable(dataset);
        return DatasetConverter.toDataset(TimeSeriesAnalyzer.rollingStd(table, column, window));
    }

    /** Add a rolling minimum column. */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset rollingMin(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("window") int window
    ) {
        Table table = DatasetConverter.toTable(dataset);
        return DatasetConverter.toDataset(TimeSeriesAnalyzer.rollingMin(table, column, window));
    }

    /** Add a rolling maximum column. */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset rollingMax(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("window") int window
    ) {
        Table table = DatasetConverter.toTable(dataset);
        return DatasetConverter.toDataset(TimeSeriesAnalyzer.rollingMax(table, column, window));
    }

    // =========================================================================
    // Descriptive statistics
    // =========================================================================

    /**
     * Compute descriptive statistics for all numeric columns.
     * Returns a Dataset with rows for count, mean, std, min, 25%, 50%, 75%, max.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset describe(@ScriptArg("dataset") Dataset dataset) {
        Table table = DatasetConverter.toTable(dataset);
        return StatisticsEngine.describe(table);
    }

    /**
     * Compute the Pearson correlation matrix for all numeric columns.
     * Returns a Dataset where the first column is the row label and the
     * remaining columns are correlation coefficients.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset correlate(@ScriptArg("dataset") Dataset dataset) {
        Table table = DatasetConverter.toTable(dataset);
        return StatisticsEngine.correlate(table);
    }

    /**
     * Normalize a column in a copy of the Dataset.
     *
     * @param dataset Ignition Dataset
     * @param column  column to normalize
     * @param method  "minmax" (scale to [0, 1]) or "zscore" (mean=0, std=1)
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset normalize(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("method") String method
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = StatisticsEngine.normalize(table, column, method);
        return DatasetConverter.toDataset(result);
    }

    // =========================================================================
    // Anomaly detection
    // =========================================================================

    /**
     * Detect anomalies using a Z-score threshold.
     * Appends a boolean column named {@code column}_anomaly.
     *
     * @param dataset   Ignition Dataset
     * @param column    numeric column to analyse
     * @param threshold sigma threshold (2.0 = sensitive, 3.0 = standard, 4.0 = conservative)
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset detectAnomaliesZScore(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("threshold") double threshold
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = AnomalyDetector.zScore(table, column, threshold);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Detect anomalies using the Interquartile Range method (Tukey fences).
     * Appends a boolean column named {@code column}_anomaly.
     *
     * @param dataset Ignition Dataset
     * @param column  numeric column to analyse
     * @param fence   IQR multiplier (1.5 = mild outliers, 3.0 = extreme outliers)
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset detectAnomaliesIQR(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("fence") double fence
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = AnomalyDetector.iqr(table, column, fence);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Detect anomalies using an EWMA control chart.
     * Suitable for slowly drifting processes where Z-score misses shifts.
     * Appends a boolean column named {@code column}_anomaly.
     *
     * @param dataset   Ignition Dataset
     * @param column    numeric column to analyse
     * @param lambda    EWMA smoothing factor (0 < lambda ≤ 1; typical: 0.2)
     * @param threshold control limit in sigma units (typical: 3.0)
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset detectAnomaliesEWMA(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("lambda") double lambda,
        @ScriptArg("threshold") double threshold
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = AnomalyDetector.ewma(table, column, lambda, threshold);
        return DatasetConverter.toDataset(result);
    }

    // =========================================================================
    // Forecasting
    // =========================================================================

    /**
     * Forecast future values using Holt's double exponential smoothing.
     * Returns a new Dataset containing only the forecasted rows — append to
     * the original for a full historical + forecast view.
     *
     * @param dataset     Ignition Dataset (must have a timestamp column)
     * @param column      numeric column to forecast
     * @param periods     number of future steps to generate
     * @param intervalStr step size: "1m", "5m", "1h", "1d"
     * @return Dataset with {@code periods} rows of forecasted timestamps and values
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset forecast(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("periods") int periods,
        @ScriptArg("intervalStr") String intervalStr
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = Forecaster.forecast(table, column, periods, intervalStr);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Forecast using simple linear extrapolation (ordinary least squares trend line).
     * Best for data with a clear monotonic trend and no complex seasonality.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset forecastLinear(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("periods") int periods,
        @ScriptArg("intervalStr") String intervalStr
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = Forecaster.linear(table, column, periods, intervalStr);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Forecast using Holt's method with explicit smoothing parameters.
     *
     * @param alpha level smoothing factor (0 < alpha < 1; lower = smoother)
     * @param beta  trend smoothing factor (0 < beta < 1)
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset forecastHolt(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("column") String column,
        @ScriptArg("periods") int periods,
        @ScriptArg("intervalStr") String intervalStr,
        @ScriptArg("alpha") double alpha,
        @ScriptArg("beta") double beta
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = Forecaster.holt(table, column, periods, intervalStr, alpha, beta);
        return DatasetConverter.toDataset(result);
    }

    // =========================================================================
    // Machine learning
    // =========================================================================

    /**
     * Cluster rows into {@code k} groups using K-Means.
     * Appends an integer column named "cluster" (values 0 to k−1).
     *
     * <p>All specified columns must be numeric. Remove or fill missing values
     * with {@code system.analytics.dropNulls} / {@code fillNulls} before calling.
     *
     * @param dataset Ignition Dataset
     * @param columns feature columns to cluster on (e.g. ["Temperature", "Pressure"])
     * @param k       number of clusters
     * @return Dataset with the original columns plus a "cluster" integer column
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset cluster(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("columns") String[] columns,
        @ScriptArg("k") int k
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = MLEngine.kmeans(table, columns, k);
        return DatasetConverter.toDataset(result);
    }

    /**
     * Reduce dimensionality using Principal Component Analysis (PCA).
     * Appends {@code nComponents} new columns named "PC_1", "PC_2", … "PC_n"
     * that capture the directions of maximum variance across the feature columns.
     *
     * <p>Input columns are standardized (mean=0, unit variance) before
     * decomposition, so columns with different units are treated equally.
     *
     * @param dataset     Ignition Dataset
     * @param columns     feature columns to decompose (must all be numeric)
     * @param nComponents number of principal components to retain (1 ≤ n ≤ columns.length)
     * @return Dataset with original columns plus "PC_1" … "PC_n" columns
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public Dataset pca(
        @ScriptArg("dataset") Dataset dataset,
        @ScriptArg("columns") String[] columns,
        @ScriptArg("nComponents") int nComponents
    ) {
        Table table = DatasetConverter.toTable(dataset);
        Table result = MLEngine.pca(table, columns, nComponents);
        return DatasetConverter.toDataset(result);
    }

    // =========================================================================
    // Export
    // =========================================================================

    /**
     * Serialize the Dataset to a CSV string.
     * Useful for writing to the file system or returning to a report.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public String toCSV(@ScriptArg("dataset") Dataset dataset) {
        Table table = DatasetConverter.toTable(dataset);
        return table.write().toString("csv");
    }

    /**
     * Serialize the Dataset to a JSON array-of-objects string.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public String toJSON(@ScriptArg("dataset") Dataset dataset) {
        Table table = DatasetConverter.toTable(dataset);
        // Tablesaw's JSON writer produces an array of row objects
        return table.write().toString("json");
    }

    // =========================================================================
    // Utility / introspection
    // =========================================================================

    /**
     * Return the shape of a Dataset as a two-element integer array: [rows, columns].
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public int[] shape(@ScriptArg("dataset") Dataset dataset) {
        return new int[] {dataset.getRowCount(), dataset.getColumnCount()};
    }

    /**
     * Return the column names of the Dataset as a String array.
     */
    @JythonElement(docBundlePrefix = DOC_BUNDLE_PREFIX)
    public String[] columnNames(@ScriptArg("dataset") Dataset dataset) {
        String[] names = new String[dataset.getColumnCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = dataset.getColumnName(i);
        }
        return names;
    }
}
