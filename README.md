# Ignition Analytics

A gateway module for Inductive Automation Ignition that brings modern data analytics capabilities to the Jython scripting environment. Time series resampling, descriptive statistics, anomaly detection, and forecasting are all available as `system.analytics.*` scripting functions that work directly with Ignition's native `Dataset` type.

## Why

Ignition's scripting engine runs Jython (Python 2.7 on the JVM). This makes it impossible to use pandas, NumPy, scikit-learn, or any CPython-native analytics library. This module brings equivalent capabilities into Ignition natively — no external Python process, no network calls, no additional infrastructure. A single `.modl` file is all that is required.

## Capabilities

| Category | Functions |
|---|---|
| **Resampling** | `resample` — bucket time series to any interval (mean / min / max / sum / first / last) |
| **Interpolation** | `interpolate` — linear, forward fill, backward fill |
| **Rolling stats** | `rollingMean`, `rollingStd`, `rollingMin`, `rollingMax` |
| **Null handling** | `dropNulls`, `fillNulls` |
| **Statistics** | `describe` — count / mean / std / min / percentiles / max per column |
| **Correlation** | `correlate` — Pearson correlation matrix |
| **Normalization** | `normalize` — min-max or z-score |
| **Anomaly detection** | `detectAnomaliesZScore`, `detectAnomaliesIQR`, `detectAnomaliesEWMA` |
| **Forecasting** | `forecast` (Holt), `forecastLinear`, `forecastHolt` |
| **Export** | `toCSV`, `toJSON` |
| **Introspection** | `shape`, `columnNames` |

## Requirements

| Requirement | Version |
|---|---|
| Ignition | 8.3.0 or later |
| Java (build only) | 17 (Zulu, Temurin, or Oracle) |
| Gradle (build only) | 8.2+ (wrapper included) |

## Quick Start

### 1. Build

```bash
git clone https://github.com/your-org/ignition-analytics-core
cd ignition-analytics-core
./gradlew build
```

The module file is written to `build/ignition-analytics.unsigned.modl`.

### 2. Install

In the Ignition Gateway web interface:

1. Navigate to **Config → Modules**
2. Click **Install or Upgrade a Module**
3. Upload `build/ignition-analytics.unsigned.modl`
4. Accept the prompts — the module will appear as **Ignition Analytics** with status **Running**

### 3. Use

```python
# In any Ignition script (gateway timer, tag change, Designer Script Console, etc.)

tagData = system.tag.queryTagHistory(
    paths=["[default]Reactor/Temperature", "[default]Reactor/Pressure"],
    startDate=system.date.addHours(system.date.now(), -24),
    endDate=system.date.now(),
    returnSize=0
)

# Downsample to 1-hour means and fill any gaps
hourly = system.analytics.resample(tagData, "1h", "mean")
clean  = system.analytics.interpolate(hourly, "linear")

# Describe the data
stats  = system.analytics.describe(clean)

# Find anomalies (z-score > 3 sigma)
flagged = system.analytics.detectAnomaliesZScore(clean, "Temperature", 3.0)

# Forecast the next 24 hours
future = system.analytics.forecast(clean, "Temperature", 24, "1h")
```

Every function accepts and returns a standard Ignition `Dataset`, so results flow directly into `system.dataset.*` functions, Power Table components, and Reporting Module data sources.

## Building from Source

### Prerequisites

- JDK 17 — [Azul Zulu](https://www.azul.com/downloads/), [Eclipse Temurin](https://adoptium.net/), or Oracle JDK
- The included Gradle wrapper handles everything else; no separate Gradle installation is needed

### Steps

```bash
# Clone
git clone https://github.com/your-org/ignition-analytics-core
cd ignition-analytics-core

# Build (downloads dependencies on first run, ~1 min)
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows

# Output
ls build/*.modl
```

The build produces an **unsigned** module. Unsigned modules can be installed on any gateway in developer mode or on gateways configured to allow unsigned modules. For production deployment, see [Signing a Module](docs/signing.md).

### Project Structure

```
ignition-analytics-core/
├── common/          # Analytics engines — loaded in Gateway, Designer, and Client
│   └── src/main/java/dev/ignition/analytics/
│       ├── gateway/scripting/   AnalyticsScriptModule.java   ← public scripting API
│       ├── gateway/util/        DatasetConverter.java         ← Ignition Dataset ↔ Tablesaw
│       ├── gateway/timeseries/  TimeSeriesAnalyzer.java
│       ├── gateway/stats/       StatisticsEngine.java
│       ├── gateway/anomaly/     AnomalyDetector.java
│       └── gateway/forecast/    Forecaster.java
├── gateway/         # Gateway hook — registers system.analytics in gateway scope
├── designer/        # Designer hook — registers system.analytics in Script Console
├── build/           # Output — ignition-analytics.unsigned.modl
└── docs/            # Extended documentation
```

## Scripting Reference

See [docs/scripting-api.md](docs/scripting-api.md) for the full function reference with examples.

## Libraries

| Library | Version | License | Purpose |
|---|---|---|---|
| [Tablesaw](https://github.com/jtablesaw/tablesaw) | 0.43.1 | Apache 2.0 | DataFrame operations |
| [Apache Commons Math](https://commons.apache.org/proper/commons-math/) | 3.6.1 | Apache 2.0 | Statistics, regression |

## License

Copyright © 2026. See [LICENSE.html](LICENSE.html).
