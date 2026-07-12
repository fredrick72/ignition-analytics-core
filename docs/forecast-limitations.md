# Forecast Function Limitations

Findings from systematic testing of `system.analytics.forecastLinear`,
`forecastHolt`, and `forecast` (aliased to Holt) against synthetic series
with known closed-form ground truth. Methodology, test data generator, and
sweep scripts are in `forecastTestData.py`, `forecastToolValidation.py`,
and `forecastLimitsCharacterization.py` (Script Library).

Test setup: synthetic series of the form
`value(t) = base + trend·t + amplitude·sin(2π·t / period) + noise(t)`,
generated with zero or controlled (seeded, reproducible) noise so forecast
output can be scored against exact expected values rather than estimated
accuracy.

---

## `forecastLinear` — pure trend

**Correctness:** exact. Against a noiseless linear series, MAE = 0.000
across a 24-step horizon. No caveats found.

**Noise robustness:** MAE scales linearly with injected noise standard
deviation, at a consistent ratio of **MAE ≈ 0.20 × noiseStdDev** across
the full range tested (σ = 0 to 40, base value 100, trend 0.5/step, 200
training points / 24-step holdout). This reflects the ~5x noise damping
you'd expect from an OLS fit over ~176 training points — the forecaster is
doing what a linear regression should. No seasonality was present in this
test; noise robustness under simultaneous seasonality has not been
isolated separately.

**Recommendation:** safe to use as-is for series that are genuinely linear
or linear-plus-noise, with no seasonal component.

---

## `forecastHolt` / `forecast` — no seasonal component

**Root cause of seasonal error:** Holt's method (double exponential
smoothing: level + trend, no seasonal term) does not model cyclical
patterns. When fit against a pure sine wave, it extrapolates the *local
slope of the curve at the moment training data ends* as if it were a
persistent linear trend, and projects that slope forward for the entire
forecast horizon — rather than averaging out to a flat or mean-reverting
prediction.

Confirmed via per-step trace (seasonalAmplitude=20, trend=0, noise=0,
α=0.3, β=0.1): predicted values climbed from 120.25 at step 0 to 163.86 at
step 23 — a ~44-unit rise — despite the underlying series having **zero
actual trend**. The forecaster locked onto the sine curve's instantaneous
slope at cutoff and ran with it indefinitely.

**Error scaling:** MAE scales linearly with seasonal amplitude, holding
trend and noise at zero:

| Amplitude | MAE | MAE / Amplitude |
|---|---|---|
| 0 | 0.000 | — |
| 5 | 10.514 | 2.103 |
| 10 | 21.028 | 2.103 |
| 15 | 31.542 | 2.103 |
| 20 | 42.056 | 2.103 |
| 30 | 63.084 | 2.103 |
| 40 | 84.111 | 2.103 |
| 60 | 126.167 | 2.104 |

The ratio is effectively constant (≈2.10) across the full range tested —
this isn't a soft degradation, it's a predictable, unbounded linear
relationship between seasonal amplitude and forecast error. For reference,
a forecaster that simply predicted the series mean (ignoring the
oscillation entirely) would land near MAE ≈ 0.637 × amplitude (2/π ×
amplitude, the mean absolute value of a sine wave) — Holt's actual error
is roughly **3.3x worse than doing nothing**, because the spurious trend
extrapolation actively moves predictions further from the true values
rather than merely failing to correct for the oscillation.

**Recommendation:** do not use `forecastHolt` or `forecast` on data with a
known seasonal/cyclical component without first deseasonalizing (e.g.
subtract or divide out the seasonal component via `resample` +
period-aligned averaging before forecasting, then reapply the seasonal
component to the forecast output). As-is, forecast error grows
proportionally and without bound as seasonal amplitude increases relative
to the base value — this should be called out explicitly in the module's
scripting reference docs so users don't reach for Holt on visibly
cyclical tag data (e.g. daily temperature curves, weekly demand patterns)
expecting it to track the cycle.

---

## Forecast horizon length

MAE grows monotonically with forecast horizon, confirming the
drift-extrapolation mechanism directly: the further out Holt projects, the
more the spurious trend (locked in from the seasonal curve's local slope
at the training cutoff) diverges from the true bounded oscillation.

Isolation method: one training set, one forecast call at the longest
horizon (96 steps), scored at multiple prefix lengths — this holds the
training cutoff and its phase within the seasonal cycle fixed, so horizon
length is the only variable changing between rows. (An initial attempt
regenerated a separate train/test split per horizon; because split point
= numPoints − holdout, that shifted the cutoff's phase within the 24-step
cycle differently for each horizon tested, producing a non-monotonic,
uninterpretable result. Corrected by scoring prefixes of a single run.)

| Horizon (steps) | MAE | RMSE | MAE / horizon |
|---|---|---|---|
| 6 | 10.631 | 11.127 | 1.772 |
| 12 | 13.208 | 15.418 | 1.101 |
| 24 | 21.822 | 24.872 | 0.909 |
| 48 | 31.476 | 36.445 | 0.656 |
| 96 | 54.772 | 62.930 | 0.571 |

Growth is monotonic but **sub-linear** — MAE per unit of horizon
*decreases* as horizon grows, rather than converging to a constant (which
is what a strictly fixed-slope extrapolation, held constant for the whole
horizon, would predict). This suggests Holt's trend component isn't
simply locked at its cutoff-time value and projected forever; its
influence appears to attenuate somewhat over a long horizon rather than
compounding at a constant rate. The exact shape of that attenuation
hasn't been characterized (would need a full per-step trace at the
96-step horizon, similar to the step-by-step trace already done at
24 steps) — flagging as a possible follow-up if the precise growth curve
matters for setting horizon limits in production use.

**Recommendation:** forecast horizon should be kept as short as
practical when using `forecastHolt`/`forecast` on any data with even mild
seasonality — error is worst not just in absolute terms but also grows
faster than proportionally at short-to-medium horizons (MAE/horizon drops
sharply between 6 and 24 steps, then levels off more gradually). If a
consuming application needs long-horizon forecasts on cyclical data,
deseasonalizing before forecasting (see previous section) matters more,
not less, as horizon increases.

---

## Summary

| Function | Use case | Status |
|---|---|---|
| `forecastLinear` | Pure trend, trend + noise | Reliable |
| `forecastHolt` / `forecast` | Trend, trend + noise (no seasonality) | Reliable |
| `forecastHolt` / `forecast` | Any data with seasonal/cyclical component | **Unreliable — error grows linearly and unbounded with amplitude, and compounds further with forecast horizon; no seasonal term exists in the current implementation** |

No Holt-Winters (trend + seasonal) forecasting function currently exists
in the module. If seasonal forecasting is a real use case for target
users (e.g. SCADA tag history with daily/weekly cycles, which is common),
this is the highest-value gap to close — every seasonal test case failed
by a wide, predictable margin.
