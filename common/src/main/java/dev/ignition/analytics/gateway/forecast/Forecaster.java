package dev.ignition.analytics.gateway.forecast;

import dev.ignition.analytics.gateway.stats.StatisticsEngine;
import dev.ignition.analytics.gateway.util.DatasetConverter;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import tech.tablesaw.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simple forecasting methods for numeric time series.
 *
 * Returns a new Table containing only the forecasted rows, with the same
 * timestamp and value columns as the input. Append to the original table for
 * a full historical + forecast view.
 */
public final class Forecaster {

    private Forecaster() {}

    // -------------------------------------------------------------------------
    // Linear extrapolation
    // -------------------------------------------------------------------------

    /**
     * Fit a least-squares linear trend to {@code column} and project forward.
     *
     * @param table       source table (must have a DateTimeColumn)
     * @param column      numeric column to forecast
     * @param periods     number of future steps to generate
     * @param intervalStr step size: "1m", "5m", "1h", "1d"
     */
    public static Table linear(Table table, String column, int periods, String intervalStr) {
        String tsColName = requireTimestampColumn(table);
        DateTimeColumn tsCol = (DateTimeColumn) table.column(tsColName);
        double[] vals = StatisticsEngine.extractDoubles(table, column);

        // Convert timestamps to epoch-minutes for the regression x-axis
        SimpleRegression reg = new SimpleRegression();
        int n = table.rowCount();
        for (int i = 0; i < n; i++) {
            if (!tsCol.isMissing(i)) {
                long x = tsCol.get(i).toEpochSecond(java.time.ZoneOffset.UTC) / 60L;
                double y = vals[Math.min(i, vals.length - 1)];
                reg.addData(x, y);
            }
        }

        // Find the last timestamp to project forward from
        LocalDateTime lastTs = tsCol.get(n - 1);
        long intervalMinutes = parseIntervalMinutes(intervalStr);

        // Build forecast table
        DateTimeColumn forecastTs = DateTimeColumn.create(tsColName, periods);
        DoubleColumn forecastVals = DoubleColumn.create(column, periods);

        for (int i = 0; i < periods; i++) {
            LocalDateTime futureTs = lastTs.plusMinutes(intervalMinutes * (i + 1));
            long x = futureTs.toEpochSecond(java.time.ZoneOffset.UTC) / 60L;
            forecastTs.set(i, futureTs);
            forecastVals.set(i, reg.predict(x));
        }

        return Table.create(table.name() + "_forecast", forecastTs, forecastVals);
    }

    // -------------------------------------------------------------------------
    // Holt (double exponential smoothing — handles trend, no seasonality)
    // -------------------------------------------------------------------------

    /**
     * Double exponential smoothing (Holt's method) for trend-aware forecasting.
     *
     * @param table       source table
     * @param column      numeric column to forecast
     * @param periods     number of future periods to generate
     * @param intervalStr step size: "1m", "5m", "1h", "1d"
     * @param alpha       level smoothing (0 < alpha < 1)
     * @param beta        trend smoothing (0 < beta < 1)
     */
    public static Table holt(
        Table table, String column, int periods, String intervalStr,
        double alpha, double beta
    ) {
        String tsColName = requireTimestampColumn(table);
        DateTimeColumn tsCol = (DateTimeColumn) table.column(tsColName);
        double[] vals = StatisticsEngine.extractDoubles(table, column);

        if (vals.length < 2) {
            throw new IllegalArgumentException("Need at least 2 observations for Holt smoothing");
        }

        // Initialise level and trend
        double level = vals[0];
        double trend = vals[1] - vals[0];

        for (int i = 1; i < vals.length; i++) {
            double prevLevel = level;
            level = alpha * vals[i] + (1.0 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1.0 - beta) * trend;
        }

        LocalDateTime lastTs = tsCol.get(table.rowCount() - 1);
        long intervalMinutes = parseIntervalMinutes(intervalStr);

        DateTimeColumn forecastTs = DateTimeColumn.create(tsColName, periods);
        DoubleColumn forecastVals = DoubleColumn.create(column, periods);

        for (int h = 1; h <= periods; h++) {
            LocalDateTime futureTs = lastTs.plusMinutes(intervalMinutes * h);
            forecastTs.set(h - 1, futureTs);
            forecastVals.set(h - 1, level + h * trend);
        }

        return Table.create(table.name() + "_forecast", forecastTs, forecastVals);
    }

    // -------------------------------------------------------------------------
    // Convenience: auto-select method
    // -------------------------------------------------------------------------

    /**
     * Forecast using the best-fit simple method: Holt with default parameters.
     * Equivalent to calling {@link #holt} with alpha=0.3, beta=0.1.
     */
    public static Table forecast(Table table, String column, int periods, String intervalStr) {
        return holt(table, column, periods, intervalStr, 0.3, 0.1);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String requireTimestampColumn(Table table) {
        return DatasetConverter.findTimestampColumn(table)
            .orElseThrow(() -> new IllegalArgumentException(
                "No timestamp column found. Ignition tag history must have a 't_stamp' column."
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
}
