# NajmTech CloudSim Optimizer

[![Java CI](https://github.com/AmmarAikan/NajmTech-CloudSim-Optimizer/actions/workflows/ci.yml/badge.svg)](https://github.com/AmmarAikan/NajmTech-CloudSim-Optimizer/actions/workflows/ci.yml)

A reproducible cloud resource-allocation simulation built with Java 17 and
CloudSim Plus 8.5.7. It models heterogeneous infrastructure, real Best-Fit VM
placement, Time-Shared workloads, network latency, and native VM cost analytics.

## What it simulates

- One datacenter with four heterogeneous hosts.
- Nine VMs across `EdgeNano`, `EdgePro`, and `EdgeUltra` tiers.
- Twenty light, medium, and heavy cloudlets.
- Actual `VmAllocationPolicyBestFit` placement.
- Time-Shared scheduling at host and VM levels.
- PE-aware Round-Robin task mapping inside each VM tier.
- A 30 Mbps broker link with 1.6 ms latency.
- CSV cloudlet details and a JSON summary with performance and cost metrics.

## Improvements over the legacy project

- Migrated from CloudSim 3.0.3 to CloudSim Plus 8.5.7.
- Removed absolute JAR paths tied to one Windows desktop.
- Corrected reversed network bandwidth/latency arguments.
- Corrected capacity planning so all nine VMs are created.
- Replaced the misleading "First-Fit order" claim with real Best-Fit placement.
- Replaced locale-dependent Arabic CSV digits with portable decimal output.
- Added Maven builds, a runnable fat JAR, JUnit tests, JSON reporting, and CI.

## Requirements

- JDK 17 or newer.
- Apache Maven 3.9 or newer, or NetBeans with bundled Maven.

## Build and test

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

On this Windows setup, `run.ps1` also detects Maven bundled with NetBeans 18:

```powershell
.\run.ps1
```

## Run

```powershell
java -jar target\najmtech-cloudsim-optimizer-1.0.0.jar
```

To select a different report directory:

```powershell
java -jar target\najmtech-cloudsim-optimizer-1.0.0.jar --output-dir custom-results
```

Generated files:

- `results/najmtech-results.csv` — one row per cloudlet.
- `results/najmtech-summary.json` — aggregate health, timing, throughput, cost,
  network, and VM-placement metrics.

See [the architecture notes](docs/ARCHITECTURE.md) for modeling decisions and
metric definitions. A verified example is available in
[`docs/sample-output`](docs/sample-output/).

## Project documents

The original course report and presentation are preserved in `docs/course/`.

## License

This project uses CloudSim Plus, which is distributed under GPL-3.0. The project
source is released under the same license; see `LICENSE`.
