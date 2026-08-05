package com.najmtech.cloudsim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NajmTechCloudSimulationTest {

    @TempDir
    Path outputDirectory;

    @Test
    void simulationCreatesEveryVmAndCompletesEveryCloudlet() throws IOException {
        final var report = new NajmTechCloudSimulation().run(outputDirectory, false);

        assertTrue(report.isHealthy());
        assertEquals(NajmTechCloudSimulation.REQUESTED_VM_COUNT, report.createdVms());
        assertEquals(0, report.failedVms());
        assertEquals(NajmTechCloudSimulation.CLOUDLET_COUNT, report.finishedCloudlets());
        assertEquals(NajmTechCloudSimulation.CLOUDLET_COUNT, report.successfulCloudlets());
        assertTrue(report.cloudlets().stream().allMatch(NajmTechCloudSimulation.CloudletResult::isSuccessful));
        assertTrue(report.cloudlets().stream().allMatch(row -> row.hostId() >= 0));
    }

    @Test
    void metricsAndNetworkUnitsAreConsistent() throws IOException {
        final var report = new NajmTechCloudSimulation().run(outputDirectory, false);

        assertEquals(30.0, report.networkBandwidthMbps());
        assertEquals(0.0016, report.networkLatencySeconds());
        assertEquals(100.0, report.successRatePercent());
        assertTrue(Double.isFinite(report.totalVmCostUsd()) && report.totalVmCostUsd() > 0.0);
        assertTrue(Double.isFinite(report.makespanSeconds()) && report.makespanSeconds() > 0.0);
        assertTrue(Double.isFinite(report.throughputCloudletsPerSecond())
            && report.throughputCloudletsPerSecond() > 0.0);
        assertTrue(report.vmsPerHost().size() >= 2, "Best-Fit should use multiple capable hosts");
        final double allocatedCloudletCost = report.cloudlets().stream()
            .mapToDouble(NajmTechCloudSimulation.CloudletResult::allocatedCostUsd)
            .sum();
        assertEquals(report.totalVmCostUsd(), allocatedCloudletCost, 0.000001);
    }

    @Test
    void reportsUsePortableMachineReadableFormatting() throws IOException {
        new NajmTechCloudSimulation().run(outputDirectory, false);

        final Path csv = outputDirectory.resolve("najmtech-results.csv");
        final Path json = outputDirectory.resolve("najmtech-summary.json");
        assertTrue(Files.isRegularFile(csv));
        assertTrue(Files.isRegularFile(json));
        assertEquals(NajmTechCloudSimulation.CLOUDLET_COUNT + 1, Files.readAllLines(csv).size());

        final String csvContent = Files.readString(csv);
        assertTrue(csvContent.matches("(?s).*\\d+\\.\\d{6}.*"));
        assertTrue(csvContent.chars().noneMatch(ch -> ch >= '\u0660' && ch <= '\u0669'));
        assertTrue(Files.readString(json).contains("\"networkLatencySeconds\": 0.001600"));
        assertTrue(Files.readString(json).contains("\"failedVms\": 0"));
    }
}
