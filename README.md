# NajmTech CloudSim — Legacy 3.0.3

This branch preserves the original NetBeans/Ant implementation as a historical
academic baseline. It uses CloudSim 3.0.3 and contains the original simulation
sources and result files.

The maintained implementation is available on the repository's `main` branch.
It migrates the scenario to CloudSim Plus, fixes the network-unit and capacity
issues, adds reproducible Maven builds, automated tests, and portable reports.

## Historical limitations

- CloudSim 3.0.3 JARs are not bundled; NetBeans expects them under `lib/`.
- The source contains two overlapping simulation implementations.
- VM placement is `VmAllocationPolicySimple`, despite an earlier First-Fit
  description in the report.
- The legacy network-link arguments are reversed.

This branch is retained for comparison and should not be used as the current
release.
