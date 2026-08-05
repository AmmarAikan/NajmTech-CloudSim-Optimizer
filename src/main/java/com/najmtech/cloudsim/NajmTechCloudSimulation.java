package com.najmtech.cloudsim;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.network.topologies.BriteNetworkTopology;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmCost;
import org.cloudsimplus.vms.VmSimple;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reproducible NajmTech cloud infrastructure simulation.
 *
 * <p>The scenario uses actual Best-Fit VM placement, Time-Shared cloudlet
 * scheduling, a correctly dimensioned network link, and CloudSim Plus native
 * VM cost accounting.</p>
 */
public final class NajmTechCloudSimulation {

    public static final int REQUESTED_VM_COUNT = 9;
    public static final int CLOUDLET_COUNT = 20;
    public static final double NETWORK_BANDWIDTH_MBPS = 30.0;
    public static final double NETWORK_LATENCY_SECONDS = 0.0016;

    private static final double COST_PER_SECOND = 0.12;
    private static final double COST_PER_MEMORY = 0.000025;
    private static final double COST_PER_STORAGE = 0.0000015;
    private static final double COST_PER_BANDWIDTH = 0.00006;

    public static void main(final String[] args) throws IOException {
        final Path outputDirectory = parseOutputDirectory(args);
        final SimulationReport report = new NajmTechCloudSimulation().run(outputDirectory, true);
        if (!report.isHealthy()) {
            throw new IllegalStateException("Simulation completed with failed VMs or cloudlets.");
        }
    }

    /** Runs an isolated simulation and writes deterministic CSV and JSON reports. */
    public SimulationReport run(final Path outputDirectory, final boolean verbose) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Log.setLevel(verbose ? Level.WARN : Level.ERROR);

        final CloudSimPlus simulation = new CloudSimPlus();
        final Datacenter datacenter = createDatacenter(simulation);
        final DatacenterBroker broker = new DatacenterBrokerSimple(simulation, "NajmTech-Broker");

        final BriteNetworkTopology topology = new BriteNetworkTopology();
        simulation.setNetworkTopology(topology);
        topology.addLink(broker, datacenter, NETWORK_BANDWIDTH_MBPS, NETWORK_LATENCY_SECONDS);

        final List<Vm> requestedVms = createVms();
        final List<Cloudlet> requestedCloudlets = createCloudlets();
        configureResourceAwareCloudletMapping(broker, requestedVms);
        broker.submitVmList(requestedVms);
        broker.submitCloudletList(requestedCloudlets);

        simulation.start();

        final List<Vm> createdVms = new ArrayList<>(broker.getVmCreatedList());
        final List<Vm> failedVms = new ArrayList<>(broker.getVmFailedList());
        final List<Cloudlet> finishedCloudlets = new ArrayList<>(broker.getCloudletFinishedList());
        finishedCloudlets.sort(Comparator.comparingLong(Cloudlet::getId));

        final Map<Vm, Double> vmCosts = calculateVmCosts(createdVms);
        final Map<Vm, Double> totalExecutionByVm = finishedCloudlets.stream()
            .collect(Collectors.groupingBy(Cloudlet::getVm, Collectors.summingDouble(Cloudlet::getTotalExecutionTime)));

        final List<CloudletResult> rows = finishedCloudlets.stream()
            .map(cloudlet -> toResult(cloudlet, vmCosts, totalExecutionByVm))
            .toList();

        final SimulationReport report = buildReport(requestedVms, createdVms, failedVms, rows, vmCosts);
        writeReports(outputDirectory, report);
        if (verbose) {
            printSummary(report, outputDirectory);
        }
        return report;
    }

    private static Datacenter createDatacenter(final CloudSimPlus simulation) {
        final int[] peCounts = {4, 6, 8, 10};
        final int[] mipsPerPe = {1200, 1800, 2600, 3200};
        final long[] ramMb = {8192, 12288, 16384, 20480};
        final long[] bandwidthMbps = {7000, 11000, 15000, 19000};
        final long[] storageMb = {750000, 1000000, 1250000, 1500000};

        final List<Host> hosts = new ArrayList<>();
        for (int hostId = 0; hostId < peCounts.length; hostId++) {
            final List<Pe> peList = new ArrayList<>();
            for (int peId = 0; peId < peCounts[hostId]; peId++) {
                peList.add(new PeSimple(mipsPerPe[hostId]));
            }
            final Host host = new HostSimple(ramMb[hostId], bandwidthMbps[hostId], storageMb[hostId], peList)
                .setVmScheduler(new VmSchedulerTimeShared());
            host.setId(hostId);
            hosts.add(host);
        }

        final Datacenter datacenter = new DatacenterSimple(simulation, hosts, new VmAllocationPolicyBestFit());
        datacenter.setName("NajmTech-Primary-Datacenter");
        datacenter.getCharacteristics()
            .setCostPerSecond(COST_PER_SECOND)
            .setCostPerMem(COST_PER_MEMORY)
            .setCostPerStorage(COST_PER_STORAGE)
            .setCostPerBw(COST_PER_BANDWIDTH);
        return datacenter;
    }

    private static List<Vm> createVms() {
        final List<VmSpec> specs = List.of(
            new VmSpec("EdgeNano", 600, 1, 1024, 1500, 8000),
            new VmSpec("EdgePro", 1400, 2, 2048, 2500, 16000),
            new VmSpec("EdgeUltra", 2400, 4, 4096, 4500, 32000)
        );

        final List<Vm> vms = new ArrayList<>();
        long vmId = 0;
        for (final VmSpec spec : specs) {
            for (int copy = 0; copy < 3; copy++) {
                final Vm vm = new VmSimple(vmId++, spec.mips(), spec.pes())
                    .setRam(spec.ramMb())
                    .setBw(spec.bandwidthMbps())
                    .setSize(spec.storageMb())
                    .setCloudletScheduler(new CloudletSchedulerTimeShared())
                    .setDescription(spec.name());
                vms.add(vm);
            }
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets() {
        final List<Cloudlet> cloudlets = new ArrayList<>();
        final UtilizationModelFull utilization = new UtilizationModelFull();
        for (int id = 0; id < CLOUDLET_COUNT; id++) {
            final long length;
            final int pes;
            if (id < 6) {
                length = 12000L + id * 800L;
                pes = 1;
            } else if (id < 13) {
                length = 26000L + (id - 6L) * 1600L;
                pes = 2;
            } else {
                length = 52000L + (id - 13L) * 2200L;
                pes = 4;
            }

            final Cloudlet cloudlet = new CloudletSimple(id, length, pes)
                .setFileSize(400L + id * 45L)
                .setOutputSize(280L + id * 30L)
                .setUtilizationModelCpu(utilization);
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    /**
     * Maps each cloudlet to a VM with the same PE count and balances cloudlets
     * round-robin inside that VM tier. This prevents multi-PE cloudlets from
     * being sent to an undersized VM by the broker's generic mapper.
     */
    private static void configureResourceAwareCloudletMapping(
        final DatacenterBroker broker,
        final List<Vm> requestedVms
    ) {
        final Map<Long, Integer> nextIndexByPes = new HashMap<>();
        broker.setVmMapper(cloudlet -> {
            final List<Vm> capableTier = requestedVms.stream()
                .filter(vm -> vm.getPesNumber() == cloudlet.getPesNumber())
                .sorted(Comparator.comparingLong(Vm::getId))
                .toList();
            if (capableTier.isEmpty()) {
                throw new IllegalStateException(
                    "No VM tier can execute cloudlet " + cloudlet.getId()
                        + " requiring " + cloudlet.getPesNumber() + " PEs"
                );
            }
            final int nextIndex = nextIndexByPes.getOrDefault(cloudlet.getPesNumber(), 0);
            nextIndexByPes.put(cloudlet.getPesNumber(), nextIndex + 1);
            return capableTier.get(nextIndex % capableTier.size());
        });
    }

    private static Map<Vm, Double> calculateVmCosts(final List<Vm> createdVms) {
        final Map<Vm, Double> costs = new HashMap<>();
        for (final Vm vm : createdVms) {
            costs.put(vm, new VmCost(vm).getTotalCost());
        }
        return costs;
    }

    private static CloudletResult toResult(
        final Cloudlet cloudlet,
        final Map<Vm, Double> vmCosts,
        final Map<Vm, Double> totalExecutionByVm
    ) {
        final Vm vm = cloudlet.getVm();
        final double vmExecution = totalExecutionByVm.getOrDefault(vm, 0.0);
        final double allocatedCost = vmExecution <= 0.0
            ? 0.0
            : vmCosts.getOrDefault(vm, 0.0) * cloudlet.getTotalExecutionTime() / vmExecution;

        return new CloudletResult(
            cloudlet.getId(),
            vm.getId(),
            vm.getHost().getId(),
            cloudlet.getStatus().name(),
            cloudlet.getStartTime(),
            cloudlet.getFinishTime(),
            cloudlet.getTotalExecutionTime(),
            Math.max(0.0, cloudlet.getStartWaitTime()),
            allocatedCost
        );
    }

    private static SimulationReport buildReport(
        final List<Vm> requestedVms,
        final List<Vm> createdVms,
        final List<Vm> failedVms,
        final List<CloudletResult> rows,
        final Map<Vm, Double> vmCosts
    ) {
        final long successful = rows.stream().filter(CloudletResult::isSuccessful).count();
        final double firstStart = rows.stream().mapToDouble(CloudletResult::startTime).min().orElse(0.0);
        final double lastFinish = rows.stream().mapToDouble(CloudletResult::finishTime).max().orElse(0.0);
        final double makespan = Math.max(0.0, lastFinish - firstStart);
        final double avgExecution = rows.stream().mapToDouble(CloudletResult::executionTime).average().orElse(0.0);
        final double avgWaiting = rows.stream().mapToDouble(CloudletResult::waitingTime).average().orElse(0.0);
        final double totalVmCost = vmCosts.values().stream().mapToDouble(Double::doubleValue).sum();

        final Map<Long, Long> vmsPerHost = createdVms.stream()
            .collect(Collectors.groupingBy(vm -> vm.getHost().getId(), LinkedHashMap::new, Collectors.counting()));

        return new SimulationReport(
            requestedVms.size(),
            createdVms.size(),
            failedVms.size(),
            rows.size(),
            (int) successful,
            makespan,
            avgExecution,
            avgWaiting,
            makespan > 0.0 ? successful / makespan : 0.0,
            totalVmCost,
            NETWORK_BANDWIDTH_MBPS,
            NETWORK_LATENCY_SECONDS,
            Map.copyOf(vmsPerHost),
            List.copyOf(rows)
        );
    }

    private static void writeReports(final Path outputDirectory, final SimulationReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        writeCsv(outputDirectory.resolve("najmtech-results.csv"), report.cloudlets());
        writeJson(outputDirectory.resolve("najmtech-summary.json"), report);
    }

    private static void writeCsv(final Path output, final List<CloudletResult> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("cloudlet_id,vm_id,host_id,status,start_time_s,finish_time_s,execution_time_s,waiting_time_s,allocated_cost_usd");
            writer.newLine();
            for (final CloudletResult row : rows) {
                writer.write(String.format(
                    Locale.ROOT,
                    "%d,%d,%d,%s,%.6f,%.6f,%.6f,%.6f,%.6f",
                    row.cloudletId(), row.vmId(), row.hostId(), row.status(),
                    row.startTime(), row.finishTime(), row.executionTime(), row.waitingTime(), row.allocatedCostUsd()
                ));
                writer.newLine();
            }
        }
    }

    private static void writeJson(final Path output, final SimulationReport report) throws IOException {
        final String hostMap = report.vmsPerHost().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> String.format(Locale.ROOT, "\"%d\":%d", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(","));
        final String json = String.format(
            Locale.ROOT,
            """
            {
              "requestedVms": %d,
              "createdVms": %d,
              "failedVms": %d,
              "finishedCloudlets": %d,
              "successfulCloudlets": %d,
              "successRatePercent": %.6f,
              "makespanSeconds": %.6f,
              "averageExecutionSeconds": %.6f,
              "averageWaitingSeconds": %.6f,
              "throughputCloudletsPerSecond": %.6f,
              "totalVmCostUsd": %.6f,
              "networkBandwidthMbps": %.6f,
              "networkLatencySeconds": %.6f,
              "vmsPerHost": {%s}
            }
            """,
            report.requestedVms(), report.createdVms(), report.failedVms(),
            report.finishedCloudlets(), report.successfulCloudlets(), report.successRatePercent(),
            report.makespanSeconds(), report.averageExecutionSeconds(), report.averageWaitingSeconds(),
            report.throughputCloudletsPerSecond(), report.totalVmCostUsd(),
            report.networkBandwidthMbps(), report.networkLatencySeconds(), hostMap
        );
        Files.writeString(output, json, StandardCharsets.UTF_8);
    }

    private static void printSummary(final SimulationReport report, final Path outputDirectory) {
        System.out.println("NajmTech CloudSim Plus simulation completed");
        System.out.println("===========================================");
        System.out.printf(Locale.ROOT, "VMs created: %d/%d (failed: %d)%n",
            report.createdVms(), report.requestedVms(), report.failedVms());
        System.out.printf(Locale.ROOT, "Cloudlets successful: %d/%d (%.2f%%)%n",
            report.successfulCloudlets(), report.finishedCloudlets(), report.successRatePercent());
        System.out.printf(Locale.ROOT, "Makespan: %.3f seconds%n", report.makespanSeconds());
        System.out.printf(Locale.ROOT, "Average execution: %.3f seconds%n", report.averageExecutionSeconds());
        System.out.printf(Locale.ROOT, "Average waiting: %.3f seconds%n", report.averageWaitingSeconds());
        System.out.printf(Locale.ROOT, "Throughput: %.6f cloudlets/second%n", report.throughputCloudletsPerSecond());
        System.out.printf(Locale.ROOT, "Total VM cost: $%.4f%n", report.totalVmCostUsd());
        System.out.printf(Locale.ROOT, "Network: %.1f Mbps, %.4f seconds latency%n",
            report.networkBandwidthMbps(), report.networkLatencySeconds());
        System.out.println("VM distribution by host: " + report.vmsPerHost());
        System.out.println("Reports: " + outputDirectory.toAbsolutePath());
    }

    private static Path parseOutputDirectory(final String[] args) {
        if (args.length == 0) {
            return Path.of("results");
        }
        if (args.length == 2 && "--output-dir".equals(args[0])) {
            return Path.of(args[1]);
        }
        throw new IllegalArgumentException("Usage: java -jar <jar> [--output-dir <directory>]");
    }

    private record VmSpec(String name, long mips, long pes, long ramMb, long bandwidthMbps, long storageMb) {
    }

    /** Immutable row written to the detailed CSV report. */
    public record CloudletResult(
        long cloudletId,
        long vmId,
        long hostId,
        String status,
        double startTime,
        double finishTime,
        double executionTime,
        double waitingTime,
        double allocatedCostUsd
    ) {
        public boolean isSuccessful() {
            return "SUCCESS".equals(status);
        }
    }

    /** Immutable high-level simulation metrics used by the CLI and tests. */
    public record SimulationReport(
        int requestedVms,
        int createdVms,
        int failedVms,
        int finishedCloudlets,
        int successfulCloudlets,
        double makespanSeconds,
        double averageExecutionSeconds,
        double averageWaitingSeconds,
        double throughputCloudletsPerSecond,
        double totalVmCostUsd,
        double networkBandwidthMbps,
        double networkLatencySeconds,
        Map<Long, Long> vmsPerHost,
        List<CloudletResult> cloudlets
    ) {
        public double successRatePercent() {
            return finishedCloudlets == 0 ? 0.0 : successfulCloudlets * 100.0 / finishedCloudlets;
        }

        public boolean isHealthy() {
            return createdVms == requestedVms
                && failedVms == 0
                && successfulCloudlets == CLOUDLET_COUNT
                && finishedCloudlets == CLOUDLET_COUNT;
        }
    }
}
