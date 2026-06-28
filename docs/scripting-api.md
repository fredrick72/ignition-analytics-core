# Scripting API Reference

All functions are available under the `system.analytics` namespace in gateway scripts, gateway timer scripts, tag change scripts, and the Designer Script Console.

Every function accepts a standard Ignition `Dataset` (the type returned by `system.tag.queryTagHistory`, `system.db.runQuery`, etc.) and returns a `Dataset`. Results can be passed directly to any Ignition function that expects a Dataset.

---

## Working with tag history

The typical entry point is `system.tag.queryTagHistory`. Set `returnSize=0` to get every recorded sample rather than a fixed row count.

```python
tagData = system.tag.queryTagHistory(
    paths=["[default]Machine/Temperature", "[default]Machine/Pressure"],
    startDate=system.date.addHours(system.date.now(), -24),
    endDate=system.date.now(),
    returnSize=0,
    aggregationMode="LastValue"
)
```

The resulting Dataset has a `t_stamp` column (Date type) followed by one column per tag. Pass it directly to any `system.analytics` function.

---

## Time series

### `resample(dataset, interval, aggregation)`

Downsample a time series to a fixed interval by grouping timestamps into buckets and aggregating each bucket.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data — must contain a Date timestamp column |
| `interval` | String | Bucket width: `"1m"`, `"5m"`, `"15m"`, `"30m"`, `"1h"`, `"4h"`, `"1d"` |
| `aggregation` | String | `"mean"`, `"min"`, `"max"`, `"sum"`, `"first"`, `"last"` |

**Returns:** Dataset with one row per bucket.

```python
# Collapse 1-minute samples to 1-hour averages
hourly = system.analytics.resample(tagData, "1h", "mean")

# Keep only the last reading in each 5-minute window
last5m = system.analytics.resample(tagData, "5m", "last")
```

---

### `interpolate(dataset, method)`

Fill missing values in all numeric columns.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `method` | String | `"linear"` — straight-line fill between surrounding values (default) |
| | | `"forward"` — carry the last known value forward (LOCF) |
| | | `"backward"` — carry the next known value backward |

**Returns:** Dataset with missing values filled.

```python
# Linear interpolation (best for smooth physical signals like temperature)
clean = system.analytics.interpolate(tagData, "linear")

# Forward fill (best for step-change signals like valve states)
filled = system.analytics.interpolate(tagData, "forward")
```

---

### `dropNulls(dataset)`

Remove all rows where any numeric column contains a missing value.

```python
clean = system.analytics.dropNulls(tagData)
```

---

### `fillNulls(dataset, column, fillValue)`

Replace missing values in a single column with a constant.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `column` | String | Column name |
| `fillValue` | Double | Replacement value |

```python
# Replace missing pressure readings with atmospheric pressure
filled = system.analytics.fillNulls(tagData, "Pressure", 14.696)
```

---

## Rolling / windowed statistics

Each rolling function adds a new column to a copy of the Dataset. The new column is named `{column}_rollMean`, `{column}_rollStd`, etc. The first `window - 1` rows will be missing (not enough preceding data to fill the window).

### `rollingMean(dataset, column, window)`

```python
# 10-sample centered moving average of Temperature
result = system.analytics.rollingMean(tagData, "Temperature", 10)
# result now has a "Temperature_rollMean" column
```

### `rollingStd(dataset, column, window)`

```python
# Rolling standard deviation — useful for detecting increasing variability
result = system.analytics.rollingStd(tagData, "Temperature", 20)
```

### `rollingMin(dataset, column, window)` / `rollingMax(dataset, column, window)`

```python
# Rolling range (max - min) as a variability measure
withMin = system.analytics.rollingMin(tagData, "Pressure", 12)
withMax = system.analytics.rollingMax(withMin, "Pressure", 12)
```

---

## Descriptive statistics

### `describe(dataset)`

Compute summary statistics for all numeric columns.

**Returns:** Dataset with 8 rows (one per statistic) and one column per numeric input column.

| Row | Statistic |
|---|---|
| `count` | Number of non-missing values |
| `mean` | Arithmetic mean |
| `std` | Sample standard deviation |
| `min` | Minimum |
| `25%` | First quartile |
| `50%` | Median |
| `75%` | Third quartile |
| `max` | Maximum |

```python
stats = system.analytics.describe(tagData)

# Use in a report or display on a screen
# stats.getValueAt(row, col) — row 0=count, 1=mean, 2=std, ...
mean_temp = stats.getValueAt(1, 1)  # mean of first numeric column
```

---

### `correlate(dataset)`

Compute the Pearson correlation matrix for all numeric columns.

**Returns:** Dataset where the first column is the row label and each subsequent column is a correlation coefficient (−1.0 to 1.0).

```python
corr = system.analytics.correlate(tagData)

# A value near 1.0 means strong positive correlation
# A value near -1.0 means strong negative correlation
# A value near 0.0 means no linear relationship
```

---

### `normalize(dataset, column, method)`

Scale a column's values onto a standard range.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `column` | String | Column to normalize |
| `method` | String | `"minmax"` — scale to [0, 1] |
| | | `"zscore"` — standardize to mean=0, std=1 |

**Returns:** Copy of the Dataset with the specified column replaced by normalized values.

```python
# Scale temperature to [0, 1] for use in a model or gauge component
normalized = system.analytics.normalize(tagData, "Temperature", "minmax")

# Standardize for z-score comparison
standardized = system.analytics.normalize(tagData, "Temperature", "zscore")
```

---

## Anomaly detection

All anomaly functions return a copy of the Dataset with an additional boolean column named `{column}_anomaly`. Rows where that column is `True` are flagged as anomalous.

### `detectAnomaliesZScore(dataset, column, threshold)`

Flag values that are more than `threshold` standard deviations from the column mean.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `column` | String | Column to analyse |
| `threshold` | Double | Sigma threshold. Use `2.0` for sensitive, `3.0` for standard, `4.0` for conservative |

```python
flagged = system.analytics.detectAnomaliesZScore(tagData, "Temperature", 3.0)

# Count anomalies
count = 0
for row in range(flagged.rowCount):
    if flagged.getValueAt(row, "Temperature_anomaly"):
        count += 1
print("Anomalies found:", count)
```

**When to use:** Best for stationary signals with a roughly normal distribution. Not effective for signals with a slowly drifting mean.

---

### `detectAnomaliesIQR(dataset, column, fence)`

Flag values outside the Tukey fences: `[Q1 − fence × IQR, Q3 + fence × IQR]`.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `column` | String | Column to analyse |
| `fence` | Double | IQR multiplier. Use `1.5` for mild outliers, `3.0` for extreme outliers |

```python
# Standard Tukey outlier detection
flagged = system.analytics.detectAnomaliesIQR(tagData, "Pressure", 1.5)
```

**When to use:** More robust than z-score for non-normal distributions (e.g., vibration, flow rate). Not affected by outliers skewing the mean and standard deviation.

---

### `detectAnomaliesEWMA(dataset, column, lambda, threshold)`

Exponentially Weighted Moving Average (EWMA) control chart. Flags points where the smoothed value deviates significantly from the process mean — effective at detecting gradual drift that z-score misses.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data (ordered by time) |
| `column` | String | Column to analyse |
| `lambda` | Double | Smoothing factor (0 < λ ≤ 1). Lower = more smoothing, more sensitive to drift. Typical: `0.2` |
| `threshold` | Double | Control limit in sigma units. Typical: `3.0` |

```python
# Detect gradual temperature drift
flagged = system.analytics.detectAnomaliesEWMA(tagData, "Temperature", 0.2, 3.0)
```

**When to use:** Best for detecting process mean shifts and slow drift. Pairs well with z-score — run both and union the results for comprehensive coverage.

---

## Forecasting

Forecast functions return a **new Dataset** containing only the forecasted rows — not the original historical rows. Append to the original for a full historical + forecast view.

### `forecast(dataset, column, periods, interval)`

Forecast using Holt's double exponential smoothing (handles trend, no seasonality). Uses default smoothing parameters (`alpha=0.3`, `beta=0.1`).

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Historical data — must contain a timestamp column |
| `column` | String | Column to forecast |
| `periods` | Integer | Number of future steps |
| `interval` | String | Step size: `"1m"`, `"5m"`, `"1h"`, `"1d"` |

**Returns:** Dataset with `periods` rows of future timestamps and forecasted values.

```python
# Forecast the next 24 hours of temperature at 1-hour resolution
future = system.analytics.forecast(hourly, "Temperature", 24, "1h")

# Combine history and forecast for a trend chart
# (both datasets must have the same column structure)
combined = system.dataset.appendDatasets(hourly, future)
```

---

### `forecastLinear(dataset, column, periods, interval)`

Forecast by fitting an ordinary least-squares trend line and extrapolating forward.

```python
# Best for data with a clear linear trend
future = system.analytics.forecastLinear(tagData, "ProductionCount", 8, "1h")
```

**When to use:** Good for monotonically increasing or decreasing signals (counters, cumulative production). Assumes the rate of change stays constant.

---

### `forecastHolt(dataset, column, periods, interval, alpha, beta)`

Holt's method with explicit smoothing parameters.

| Parameter | Type | Description |
|---|---|---|
| `alpha` | Double | Level smoothing (0 < α < 1). Higher = more weight on recent values |
| `beta` | Double | Trend smoothing (0 < β < 1). Higher = more reactive to trend changes |

```python
# More reactive to recent trend changes
future = system.analytics.forecastHolt(tagData, "Temperature", 12, "1h", 0.5, 0.3)
```

---

## Machine learning

These functions require the SMILE library (bundled in the module — no extra installation). Missing values in the feature columns will cause errors; use `dropNulls` or `fillNulls` to clean the data first.

### `cluster(dataset, columns, k)`

Partition rows into `k` groups using K-Means. Each row is assigned to the nearest cluster centroid in the feature space defined by `columns`. Useful for discovering operating modes, process states, or product families in historical data.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `columns` | String[] | Numeric feature columns to cluster on |
| `k` | Integer | Number of clusters |

**Returns:** Dataset with the original columns plus an integer column named `cluster` (values 0 to k−1).

```python
# Discover operating modes from two process variables
tagData = system.tag.queryTagHistory(
    paths=["[default]Reactor/Temperature", "[default]Reactor/Pressure"],
    startDate=system.date.addHours(system.date.now(), -168),
    endDate=system.date.now(),
    returnSize=0
)

hourly = system.analytics.resample(tagData, "1h", "mean")
clean  = system.analytics.dropNulls(hourly)

# Find 3 operating clusters
clustered = system.analytics.cluster(clean, ["Temperature", "Pressure"], 3)
# clustered now has a "cluster" column with values 0, 1, or 2

# Count rows in each cluster
for mode in range(3):
    count = sum(1 for r in range(clustered.rowCount)
                if clustered.getValueAt(r, "cluster") == mode)
    print("Mode %d: %d hours" % (mode, count))
```

**When to use:** When you want to segment data into distinct groups without prior knowledge of the groups. K-Means finds natural clusters in multi-dimensional sensor space — for example, identifying low/medium/high production states from temperature + pressure + flow rate.

---

### `pca(dataset, columns, nComponents)`

Reduce the dimensionality of the feature columns using Principal Component Analysis. The returned principal components (PC_1, PC_2, …) are linear combinations of the input features that capture the most variance in the data.

Input columns are standardized to mean=0 / unit variance before decomposition, so columns with different physical units (°C, bar, L/min) are treated equally.

| Parameter | Type | Description |
|---|---|---|
| `dataset` | Dataset | Source data |
| `columns` | String[] | Numeric feature columns to decompose |
| `nComponents` | Integer | Number of principal components to retain (1 ≤ n ≤ len(columns)) |

**Returns:** Dataset with the original columns plus `nComponents` new `DoubleColumn`s named `PC_1`, `PC_2`, …, `PC_n`.

```python
# Compress 5 correlated sensor readings into 2 principal components
tagData = system.tag.queryTagHistory(
    paths=[
        "[default]Compressor/Suction_Temp",
        "[default]Compressor/Discharge_Temp",
        "[default]Compressor/Suction_Pressure",
        "[default]Compressor/Discharge_Pressure",
        "[default]Compressor/Vibration",
    ],
    startDate=system.date.addHours(system.date.now(), -24),
    endDate=system.date.now(),
    returnSize=0
)

clean = system.analytics.dropNulls(tagData)

reduced = system.analytics.pca(
    clean,
    ["Suction_Temp", "Discharge_Temp", "Suction_Pressure", "Discharge_Pressure", "Vibration"],
    2
)
# reduced has PC_1 and PC_2 columns — plot these to visualize the operating envelope

# Feed the reduced data into K-Means for mode detection
clustered = system.analytics.cluster(reduced, ["PC_1", "PC_2"], 3)
```

**When to use:** When you have many correlated sensor tags and want to:
- Reduce noise by keeping only the dominant variance directions
- Visualize high-dimensional data in 2D (PC_1 vs PC_2)
- Pre-process data before clustering (PCA + K-Means is a classic combination)

---

## Export

### `toCSV(dataset)`

Serialize a Dataset to a CSV-formatted string.

```python
csv = system.analytics.toCSV(hourly)

# Write to a file
path = system.file.writeTempFile("analytics_export.csv", csv, "UTF-8")

# Or log the first 500 chars for debugging
logger = system.util.getLogger("Analytics")
logger.info(csv[:500])
```

---

### `toJSON(dataset)`

Serialize a Dataset to a JSON array-of-objects string.

```python
json = system.analytics.toJSON(flagged)
# Useful for sending results to a REST endpoint or storing in a tag
system.tag.writeBlocking(["[default]Analytics/LastResult"], [json])
```

---

## Introspection

### `shape(dataset)`

Return the dimensions of a Dataset as a two-element integer array `[rows, columns]`.

```python
dims = system.analytics.shape(tagData)
print("Rows:", dims[0], "Columns:", dims[1])
```

### `columnNames(dataset)`

Return the column names as a String array.

```python
names = list(system.analytics.columnNames(tagData))
print(names)  # ['t_stamp', 'Temperature', 'Pressure']
```

---

## Full example — production quality analysis

```python
def analyseShift(tagPaths, shiftStart, shiftEnd):
    """
    Pull tag history for a production shift, clean it, detect anomalies,
    and return a summary Dataset for display on a Perspective view.
    """
    # 1. Pull raw history
    raw = system.tag.queryTagHistory(
        paths=tagPaths,
        startDate=shiftStart,
        endDate=shiftEnd,
        returnSize=0,
        aggregationMode="LastValue"
    )

    # 2. Resample to 1-minute means and fill gaps
    resampled = system.analytics.resample(raw, "1m", "mean")
    clean = system.analytics.interpolate(resampled, "linear")

    # 3. Normalize each value column for display (optional)
    tagName = system.analytics.columnNames(clean)[1]  # first value column
    normalized = system.analytics.normalize(clean, tagName, "minmax")

    # 4. Anomaly detection — combine z-score and EWMA
    afterZ    = system.analytics.detectAnomaliesZScore(clean, tagName, 3.0)
    afterEWMA = system.analytics.detectAnomaliesEWMA(clean, tagName, 0.2, 3.0)

    # 5. Summary statistics
    stats = system.analytics.describe(clean)

    return stats

# Call from a button script or scheduled gateway task
shiftStart = system.date.addHours(system.date.now(), -8)
shiftEnd   = system.date.now()
summary    = analyseShift(
    ["[default]Line1/Temperature", "[default]Line1/Pressure"],
    shiftStart,
    shiftEnd
)
```
