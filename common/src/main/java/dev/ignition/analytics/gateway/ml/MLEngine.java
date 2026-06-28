package dev.ignition.analytics.gateway.ml;

import smile.clustering.KMeans;
import smile.projection.PCA;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

/**
 * Machine learning functions backed by the SMILE library.
 *
 * All methods accept and return Tablesaw Tables so they integrate cleanly with
 * the existing engine pipeline. Missing values in the specified columns should
 * be filled or dropped before calling these methods — use
 * {@code system.analytics.dropNulls} or {@code system.analytics.fillNulls} first.
 */
public final class MLEngine {

    private MLEngine() {}

    // -------------------------------------------------------------------------
    // K-Means clustering
    // -------------------------------------------------------------------------

    /**
     * Assign each row to one of {@code k} clusters using K-Means.
     * Appends an {@code IntColumn} named "cluster" (values 0 to k−1).
     *
     * @param table   source table
     * @param columns feature columns to cluster on (must all be numeric)
     * @param k       number of clusters
     */
    public static Table kmeans(Table table, String[] columns, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one feature column is required");
        }

        int n = table.rowCount();
        double[][] data = extractMatrix(table, columns);

        KMeans model = KMeans.fit(data, k);

        Table result = table.copy();
        IntColumn clusterCol = IntColumn.create("cluster", n);
        for (int i = 0; i < n; i++) {
            clusterCol.set(i, model.y[i]);
        }
        result.addColumns(clusterCol);
        return result;
    }

    // -------------------------------------------------------------------------
    // Principal Component Analysis
    // -------------------------------------------------------------------------

    /**
     * Project the specified columns into a lower-dimensional space using PCA.
     * Appends {@code nComponents} new columns named "PC_1", "PC_2", … "PC_n".
     *
     * <p>The input columns are standardized internally (mean=0, unit variance)
     * before computing eigenvectors, which is the standard correlation-matrix PCA
     * recommended when columns have different physical units.
     *
     * @param table       source table
     * @param columns     feature columns to decompose (must all be numeric)
     * @param nComponents number of principal components to retain (1 ≤ n ≤ columns.length)
     */
    public static Table pca(Table table, String[] columns, int nComponents) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one feature column is required");
        }
        if (nComponents < 1 || nComponents > columns.length) {
            throw new IllegalArgumentException(
                "nComponents must be between 1 and the number of feature columns (" + columns.length + ")"
            );
        }

        int n = table.rowCount();
        double[][] data = extractMatrix(table, columns);

        PCA model = PCA.fit(data);
        model.setProjection(nComponents);
        double[][] projected = model.project(data);

        Table result = table.copy();
        for (int k = 0; k < nComponents; k++) {
            DoubleColumn pc = DoubleColumn.create("PC_" + (k + 1), n);
            for (int i = 0; i < n; i++) {
                pc.set(i, projected[i][k]);
            }
            result.addColumns(pc);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Build a row-major double[][] from the specified columns.
     * Missing values are passed through as Double.NaN — callers should ensure
     * the data is clean before invoking ML algorithms.
     */
    private static double[][] extractMatrix(Table table, String[] columns) {
        int n = table.rowCount();
        int p = columns.length;
        double[][] data = new double[n][p];

        for (int j = 0; j < p; j++) {
            Column<?> col = table.column(columns[j]);
            DoubleColumn dc;
            if (col instanceof DoubleColumn) {
                dc = (DoubleColumn) col;
            } else if (col instanceof IntColumn) {
                dc = ((IntColumn) col).asDoubleColumn();
            } else if (col instanceof LongColumn) {
                dc = ((LongColumn) col).asDoubleColumn();
            } else {
                throw new IllegalArgumentException(
                    "Column '" + columns[j] + "' is not numeric (type: " + col.type() + ")"
                );
            }

            for (int i = 0; i < n; i++) {
                data[i][j] = dc.isMissing(i) ? Double.NaN : dc.get(i);
            }
        }

        return data;
    }
}
