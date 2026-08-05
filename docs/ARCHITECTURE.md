# Simulation design

The NajmTech scenario models one datacenter, four heterogeneous physical hosts,
nine virtual machines, and twenty cloudlets.

## Placement and scheduling

- `VmAllocationPolicyBestFit` performs real Best-Fit host selection. This is not
  merely a sorted submission list.
- `VmSchedulerTimeShared` shares host processing elements among VMs.
- `CloudletSchedulerTimeShared` shares each VM among its assigned cloudlets.
- A PE-aware broker mapper assigns 1-, 2-, and 4-PE cloudlets to the matching
  VM tier and balances work round-robin inside each tier.
- Host PE capacities are dimensioned so all three `EdgeUltra` VMs can be placed.

## Network model

CloudSim Plus accepts bandwidth in megabits per second and latency in seconds.
The broker-to-datacenter link therefore uses `30.0` Mbps and `0.0016` seconds,
which represents 1.6 milliseconds. The legacy project accidentally reversed
these arguments and simulated a 30-second delay.

## Cost model

Datacenter CPU, memory, storage, and bandwidth prices are configured through
CloudSim Plus characteristics. `VmCost` calculates each VM's total cost. The
detailed CSV allocates that VM cost across its cloudlets in proportion to their
execution time, so row-level costs sum to the native VM total.

## Reproducibility checks

The JUnit suite requires all nine VMs to be created, all twenty cloudlets to
finish successfully, every result to reference a real host, finite positive
metrics, and machine-readable CSV/JSON files with locale-independent decimals.
