# Installation Guide

## Prerequisites

### Ignition Gateway

- Ignition 8.3.0 or later
- The gateway must be configured to allow **unsigned modules** (or the module must be signed — see [Signing](#signing) below)

To enable unsigned modules, go to **Config → Gateway Settings → Allow Unsigned Modules** and set it to `true`. This setting is appropriate for development and internal deployments.

### Build machine (only required if building from source)

- JDK 17 — [Azul Zulu](https://www.azul.com/downloads/), [Eclipse Temurin](https://adoptium.net/), or Oracle JDK
- The Gradle wrapper (`gradlew` / `gradlew.bat`) is included — no separate Gradle installation needed

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/your-org/ignition-analytics-core
cd ignition-analytics-core

# Build (first run downloads dependencies — requires internet access)
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

The output is:

```
build/ignition-analytics.unsigned.modl
```

Build time is approximately 30–60 seconds on the first run (dependency download), under 15 seconds on subsequent runs.

---

## Installing the Module

### Via the Gateway web interface (recommended)

1. Open `http://<your-gateway>:8088/main/setting-modules` in a browser
2. Scroll to the bottom and click **Install or Upgrade a Module**
3. Choose the `.modl` file and click **Install**
4. The module will appear in the list as **Ignition Analytics** with status **Running**

### Via the filesystem

Copy the `.modl` file directly to the gateway's module directory and restart the gateway:

```
<ignition-install>/user-lib/modules/ignition-analytics.unsigned.modl
```

Default install locations:

| OS | Path |
|---|---|
| Windows | `C:\Program Files\Inductive Automation\Ignition\user-lib\modules\` |
| Linux | `/usr/local/bin/ignition/user-lib/modules/` |
| Docker | `/usr/local/bin/ignition/user-lib/modules/` |

Restart the gateway after copying:

```bash
# Linux service
sudo systemctl restart ignition

# Windows service
net stop "Ignition Gateway"
net start "Ignition Gateway"
```

---

## Verifying the Installation

### Step 1 — Module list

Open `http://<your-gateway>:8088/main/setting-modules`. You should see:

```
Ignition Analytics    0.1.0    Running
```

A red status or error banner means the hook threw an exception. Check the logs next.

### Step 2 — Gateway logs

Open `<ignition-install>/logs/wrapper.log` and search for these three lines (order can vary — `initializeScriptManager` may run before or after `startup()`):

```
Setting up Ignition Analytics module
Starting Ignition Analytics module v0.1.0
Registered scripting namespace: system.analytics
```

All three lines present with no exception after them means a clean startup.

### Step 3 — Script Console smoke test

Open the Ignition Designer and go to **Tools → Script Console**. Run:

```python
headers = ["t_stamp", "Temperature", "Pressure"]
data = []
for i in range(20):
    data.append([
        system.date.addMinutes(system.date.now(), -(20 - i) * 60),
        65.0 + i * 0.5,
        14.7 - i * 0.02
    ])

ds = system.dataset.toDataSet(headers, data)

print("Shape:", system.analytics.shape(ds))
print("Columns:", list(system.analytics.columnNames(ds)))
print("Stats rows:", system.analytics.describe(ds).rowCount)
print("Resampled rows:", system.analytics.resample(ds, "4h", "mean").rowCount)
flagged = system.analytics.detectAnomaliesZScore(ds, "Temperature", 2.5)
print("Anomaly column added:", flagged.columnCount == ds.columnCount + 1)
```

Expected output:

```
Shape: [20, 3]
Columns: [t_stamp, Temperature, Pressure]
Stats rows: 8
Resampled rows: 5
Anomaly column added: True
```

---

## Upgrading

Install the new `.modl` using the same **Install or Upgrade a Module** button. The gateway performs a hot-swap — existing scripts continue running without a full gateway restart.

## Uninstalling

On the Modules page, click the **Uninstall** link next to Ignition Analytics. The `system.analytics` namespace is removed immediately; any scripts that call it will fail with `AttributeError` until the module is reinstalled.

---

## Signing

Unsigned modules are suitable for development and internal environments where you control the gateway. For deployment to a gateway that isn't in developer mode (or doesn't have unsigned modules explicitly allowed), the module must be signed.

The build signs automatically, with no `build.gradle.kts` changes needed, if a `signing/signing.properties` file is present — see [signing/README.md](../signing/README.md) for how to generate your own self-signed keystore (free, takes a few minutes) or wire in a CA-issued certificate for broader distribution. Without that file, `./gradlew build` produces the unsigned `.modl` as described above.

A signed build outputs `build/ignition-analytics.modl` (no `.unsigned` suffix) and can be installed on any Ignition gateway without enabling unsigned module support.
