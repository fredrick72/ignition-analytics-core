package dev.ignition.analytics.gateway.util;

import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.Dataset;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Converts between Ignition's Dataset and Tablesaw's Table.
 *
 * Ignition Dataset column types supported: Double/Float, Integer/Long,
 * String, Date (java.util.Date), Boolean.
 */
public final class DatasetConverter {

    private DatasetConverter() {}

    /** Convert an Ignition Dataset to a Tablesaw Table. */
    public static Table toTable(Dataset ds) {
        if (ds == null) {
            throw new IllegalArgumentException("Dataset must not be null");
        }

        int numCols = ds.getColumnCount();
        int numRows = ds.getRowCount();

        List<Column<?>> columns = new ArrayList<>(numCols);

        for (int c = 0; c < numCols; c++) {
            String name = ds.getColumnName(c);
            Class<?> type = ds.getColumnType(c);
            columns.add(buildColumn(name, type, ds, c, numRows));
        }

        return Table.create("data", columns);
    }

    /** Convert a Tablesaw Table back to an Ignition Dataset. */
    public static Dataset toDataset(Table table) {
        if (table == null) {
            throw new IllegalArgumentException("Table must not be null");
        }

        int numCols = table.columnCount();
        int numRows = table.rowCount();

        String[] colNames = new String[numCols];
        Class<?>[] colTypes = new Class<?>[numCols];
        Object[][] data = new Object[numRows][numCols];

        for (int c = 0; c < numCols; c++) {
            Column<?> col = table.column(c);
            colNames[c] = col.name();
            fillColumnData(col, c, numRows, colTypes, data);
        }

        return new BasicDataset(colNames, colTypes, data);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Column<?> buildColumn(String name, Class<?> type, Dataset ds, int colIdx, int numRows) {
        if (type == Double.class || type == Float.class) {
            DoubleColumn col = DoubleColumn.create(name, numRows);
            for (int r = 0; r < numRows; r++) {
                Object val = ds.getValueAt(r, colIdx);
                if (val == null) {
                    col.setMissing(r);
                } else {
                    col.set(r, ((Number) val).doubleValue());
                }
            }
            return col;
        }

        if (type == Integer.class || type == Long.class) {
            LongColumn col = LongColumn.create(name, numRows);
            for (int r = 0; r < numRows; r++) {
                Object val = ds.getValueAt(r, colIdx);
                if (val == null) {
                    col.setMissing(r);
                } else {
                    col.set(r, ((Number) val).longValue());
                }
            }
            return col;
        }

        if (type == Date.class) {
            DateTimeColumn col = DateTimeColumn.create(name, numRows);
            for (int r = 0; r < numRows; r++) {
                java.util.Date val = (java.util.Date) ds.getValueAt(r, colIdx);
                if (val == null) {
                    col.setMissing(r);
                } else {
                    col.set(r, val.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime());
                }
            }
            return col;
        }

        if (type == Boolean.class) {
            BooleanColumn col = BooleanColumn.create(name, numRows);
            for (int r = 0; r < numRows; r++) {
                Object val = ds.getValueAt(r, colIdx);
                if (val == null) {
                    col.setMissing(r);
                } else {
                    col.set(r, (Boolean) val);
                }
            }
            return col;
        }

        // Default: treat as String
        StringColumn col = StringColumn.create(name, numRows);
        for (int r = 0; r < numRows; r++) {
            Object val = ds.getValueAt(r, colIdx);
            col.set(r, val == null ? "" : val.toString());
        }
        return col;
    }

    private static void fillColumnData(
        Column<?> col, int colIdx, int numRows,
        Class<?>[] colTypes, Object[][] data
    ) {
        if (col instanceof DoubleColumn) {
            DoubleColumn dc = (DoubleColumn) col;
            colTypes[colIdx] = Double.class;
            for (int r = 0; r < numRows; r++) {
                data[r][colIdx] = dc.isMissing(r) ? null : dc.get(r);
            }
        } else if (col instanceof LongColumn) {
            LongColumn lc = (LongColumn) col;
            colTypes[colIdx] = Long.class;
            for (int r = 0; r < numRows; r++) {
                data[r][colIdx] = lc.isMissing(r) ? null : lc.get(r);
            }
        } else if (col instanceof IntColumn) {
            IntColumn ic = (IntColumn) col;
            colTypes[colIdx] = Integer.class;
            for (int r = 0; r < numRows; r++) {
                data[r][colIdx] = ic.isMissing(r) ? null : ic.get(r);
            }
        } else if (col instanceof DateTimeColumn) {
            DateTimeColumn dtc = (DateTimeColumn) col;
            colTypes[colIdx] = Date.class;
            for (int r = 0; r < numRows; r++) {
                if (dtc.isMissing(r)) {
                    data[r][colIdx] = null;
                } else {
                    data[r][colIdx] = Date.from(dtc.get(r).toInstant(ZoneOffset.UTC));
                }
            }
        } else if (col instanceof BooleanColumn) {
            BooleanColumn bc = (BooleanColumn) col;
            colTypes[colIdx] = Boolean.class;
            for (int r = 0; r < numRows; r++) {
                data[r][colIdx] = bc.isMissing(r) ? null : bc.get(r);
            }
        } else {
            colTypes[colIdx] = String.class;
            for (int r = 0; r < numRows; r++) {
                data[r][colIdx] = col.isMissing(r) ? null : col.getString(r);
            }
        }
    }

    /**
     * Find the first DateTimeColumn in the table — used as the time axis for
     * time series operations. Ignition tag history puts t_stamp first by convention.
     */
    public static Optional<String> findTimestampColumn(Table table) {
        for (Column<?> c : table.columns()) {
            if (c instanceof DateTimeColumn) {
                return Optional.of(c.name());
            }
        }
        return Optional.empty();
    }

    /** Return all numeric (Double/Long/Int) column names, excluding the timestamp column. */
    public static List<String> numericColumnNames(Table table, String excludeColumn) {
        List<String> names = new ArrayList<>();
        for (Column<?> col : table.columns()) {
            if (col.name().equals(excludeColumn)) {
                continue;
            }
            if (col instanceof DoubleColumn || col instanceof LongColumn || col instanceof IntColumn) {
                names.add(col.name());
            }
        }
        return names;
    }
}
