package cloudsim_4;

import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

// ✅ added
import java.util.Map;
import java.util.HashMap;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class Cloudsim_4 {

    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;
    private static List<Datacenter> datacenters = new ArrayList<>();
    private static List<String[]> csvData = new ArrayList<>();

    // ✅ added: store characteristics by datacenter id
    private static Map<Integer, DatacenterCharacteristics> dcCharMap = new HashMap<>();

    public static void main(String[] args) {
        Log.printLine("Starting CloudSim Project with Network Topology and BestFit Optimization");
        Log.printLine("========================================================================");

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            setupNetworkTopology();

            Datacenter datacenter = createDatacenter("Company_Datacenter", 0);
            datacenters.add(datacenter);

            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            Log.printLine("\n==== Creating VMs with BestFit ====");
            createVMsWithBestFit(brokerId);
            broker.submitVmList(vmlist);

            Log.printLine("\n==== Creating Cloudlets with TimeShared ====");
            createCloudletsWithTimeShared(brokerId);
            broker.submitCloudletList(cloudletList);

            Log.printLine("\n==== Starting Simulation ====");
            CloudSim.startSimulation();

            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();

            analyzeResultsAndCalculateCosts(finishedCloudlets);

            printConsoleResults(finishedCloudlets);

            saveResultsToCSV("simulation_results.csv");

            CloudSim.stopSimulation();

            Log.printLine("\nSimulation completed successfully!");
            Log.printLine("File created: simulation_results.csv");

        } catch (Exception e) {
            Log.printLine("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupNetworkTopology() {
        Log.printLine("\n==== Setting up Network Topology ====");
        Log.printLine("Topology: 5 Nodes, 8 Edges");
        Log.printLine("Model: RTWaxman");

        NetworkTopology.addLink(2, 0, 3.0, 10.0);
        // NetworkTopology.addLink(2, 1, 4.0, 10.0);
        NetworkTopology.addLink(3, 0, 2.828, 10.0);
        // NetworkTopology.addLink(3, 1, 3.606, 10.0);
        NetworkTopology.addLink(4, 3, 2.0, 10.0);
        NetworkTopology.addLink(4, 2, 1.0, 10.0);
        NetworkTopology.addLink(0, 4, 2.0, 10.0);
        // NetworkTopology.addLink(1, 4, 3.0, 10.0);

        Log.printLine("Network Topology created with 5 nodes and 8 edges");
        Log.printLine("Nodes: 0(DC1), 1(DC2), 2(Router), 3(Switch), 4(Broker)");
        Log.printLine("Delay: 1.0ms to 4.0ms");
        Log.printLine("Bandwidth: 10.0 Mbps per link");
    }

    private static Datacenter createDatacenter(String name, int id) {
        try {
            List<Host> hostList = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                List<Pe> peList = new ArrayList<>();
                int pes = 2 + (i * 2);
                int mips = 1000 + (i * 500);

                for (int j = 0; j < pes; j++) {
                    peList.add(new Pe(j, new PeProvisionerSimple(mips)));
                }

                int ram = 4096 + (i * 4096);
                long storage = 500000L + (i * 500000L);
                int bw = 5000 + (i * 5000);

                Host host = new Host(
                    id * 10 + i,
                    new RamProvisionerSimple(ram),
                    new BwProvisionerSimple(bw),
                    storage,
                    peList,
                    new VmSchedulerTimeShared(peList)
                );
                hostList.add(host);

                Log.printLine("   Host " + (id * 10 + i) + ": " + pes + "x" + mips +
                            " MIPS, " + ram + "MB RAM, " + (storage/1000) + "GB Storage, " +
                            bw + "Mbps BW");
            }

            double costPerSecond = 0.15;
            double costPerMemory = 0.00003;
            double costPerStorage = 0.000002;
            double costPerBandwidth = 0.00008;

            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen",
                hostList,
                3.0,
                costPerSecond,
                costPerMemory,
                costPerStorage,
                costPerBandwidth
            );

            Log.printLine("\nDatacenter Costs:");
            Log.printLine("   - Cost per Second: $" + costPerSecond);
            Log.printLine("   - Cost per Memory: $" + costPerMemory + " per MB");
            Log.printLine("   - Cost per Storage: $" + costPerStorage + " per MB");
            Log.printLine("   - Cost per Bandwidth: $" + costPerBandwidth + " per Mbps");

            Datacenter dc = new Datacenter(
                name,
                characteristics,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(),
                0
            );

            // ✅ store characteristics by actual CloudSim entity id
            dcCharMap.put(dc.getId(), characteristics);

            return dc;

        } catch (Exception e) {
            Log.printLine("Error creating Datacenter: " + e.getMessage());
            return null;
        }
    }

    private static DatacenterBroker createBroker() {
        try {
            DatacenterBroker broker = new DatacenterBroker("CompanyBroker");
            Log.printLine("Broker created: " + broker.getName() + " (ID: " + broker.getId() + ")");
            return broker;
        } catch (Exception e) {
            Log.printLine("Error creating Broker: " + e.getMessage());
            return null;
        }
    }

    private static void createVMsWithBestFit(int brokerId) {
        vmlist = new ArrayList<>();

        String[] vmTypes = {"Small", "Medium", "Large"};
        int[][] vmConfigs = {
            {500, 1024, 1000, 10000, 1},
            {1000, 2048, 2000, 20000, 2},
            {2000, 4096, 4000, 40000, 4}
        };

        for (int i = 0; i < vmConfigs.length; i++) {
            for (int j = 0; j < 3; j++) {
                int vmId = i * 10 + j;

                Vm vm = new Vm(
                    vmId,
                    brokerId,
                    vmConfigs[i][0],
                    vmConfigs[i][4],
                    vmConfigs[i][1],
                    vmConfigs[i][2],
                    vmConfigs[i][3],
                    "Xen",
                    new CloudletSchedulerTimeShared()
                );

                vmlist.add(vm);
            }

            Log.printLine("   " + vmTypes[i] + " VM: " +
                         vmConfigs[i][0] + " MIPS, " +
                         vmConfigs[i][1] + "MB RAM, " +
                         vmConfigs[i][3] + "MB Storage, " +
                         vmConfigs[i][4] + " Processors");
        }

        Collections.sort(vmlist, new Comparator<Vm>() {
            @Override
            public int compare(Vm v1, Vm v2) {
                double v1Resources = v1.getMips() * v1.getNumberOfPes() + v1.getRam();
                double v2Resources = v2.getMips() * v2.getNumberOfPes() + v2.getRam();

                if (v1Resources > v2Resources) {
                    return -1;
                } else if (v1Resources < v2Resources) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        Log.printLine("Created " + vmlist.size() + " VMs with BestFit");
        Log.printLine("(Sorted in descending order: Large → Medium → Small)");
    }

    private static void createCloudletsWithTimeShared(int brokerId) {
        cloudletList = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < 20; i++) {
            int length, pesNumber;

            if (i < 7) {
                length = 15000 + (i * 1000);
                pesNumber = 1;
            } else if (i < 14) {
                length = 35000 + ((i-7) * 2000);
                pesNumber = 2;
            } else {
                length = 60000 + ((i-14) * 3000);
                pesNumber = 4;
            }

            Cloudlet cloudlet = new Cloudlet(
                i,
                length,
                pesNumber,
                300 + (i * 50),
                300 + (i * 30),
                utilizationModel,
                utilizationModel,
                utilizationModel
            );

            cloudlet.setUserId(brokerId);
            cloudletList.add(cloudlet);
        }

        Log.printLine("Created " + cloudletList.size() + " Cloudlets");
        Log.printLine("   - 7 light tasks (15000-21000 MI)");
        Log.printLine("   - 7 medium tasks (35000-47000 MI)");
        Log.printLine("   - 6 heavy tasks (60000-75000 MI)");
        Log.printLine("   - Scheduling policy: TimeShared");
    }

    private static void analyzeResultsAndCalculateCosts(List<Cloudlet> cloudlets) {
        if (cloudlets == null || cloudlets.isEmpty()) {
            Log.printLine("No results to analyze");
            return;
        }

        DecimalFormat df = new DecimalFormat("0.00");
        double totalCost = 0;
        double totalExecutionTime = 0;
        double totalWaitingTime = 0;
        int successCount = 0;

        Log.printLine("\n==== Results Analysis and Cost Calculation ====");

        for (Cloudlet cloudlet : cloudlets) {
            String status = (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) ? "SUCCESS" : "FAILED";
            double cost = 0;
            double execTime = 0;
            double startTime = 0;
            double finishTime = 0;

            if (status.equals("SUCCESS")) {
                successCount++;
                execTime = cloudlet.getActualCPUTime();
                startTime = cloudlet.getExecStartTime();
                finishTime = cloudlet.getFinishTime();

                cost = calculateCloudletCost(cloudlet);

                totalCost += cost;
                totalExecutionTime += execTime;
                totalWaitingTime += startTime;
            }

            csvData.add(new String[]{
                String.valueOf(cloudlet.getCloudletId()),
                String.valueOf(cloudlet.getVmId()),
                String.valueOf(cloudlet.getResourceId()),
                df.format(startTime),
                df.format(finishTime),
                df.format(execTime),
                df.format(cost),
                status
            });
        }

        Log.printLine("\nPerformance Statistics:");
        Log.printLine("   - Successful Cloudlets: " + successCount + "/" + cloudlets.size());
        Log.printLine("   - Success Rate: " + df.format((successCount * 100.0) / cloudlets.size()) + "%");

        if (successCount > 0) {
            Log.printLine("   - Total Execution Time: " + df.format(totalExecutionTime) + " seconds");
            Log.printLine("   - Average Execution Time: " + df.format(totalExecutionTime / successCount) + " seconds");
            Log.printLine("   - Total Waiting Time: " + df.format(totalWaitingTime) + " seconds");
            Log.printLine("   - Average Waiting Time: " + df.format(totalWaitingTime / successCount) + " seconds");
            Log.printLine("   - Total Cost: $" + df.format(totalCost));
            Log.printLine("   - Average Cost per Cloudlet: $" + df.format(totalCost / successCount));
        }
    }

    private static double calculateCloudletCost(Cloudlet cloudlet) {
        // ✅ get characteristics without calling Datacenter.getCharacteristics()
        DatacenterCharacteristics characteristics = dcCharMap.get(cloudlet.getResourceId());

        double costPerSecond = 0.15;
        double costPerMemory = 0.00003;
        double costPerStorage = 0.000002;
        double costPerBandwidth = 0.00008;

        if (characteristics != null) {
            costPerSecond = characteristics.getCostPerSecond();
            costPerMemory = characteristics.getCostPerMem();
            costPerStorage = characteristics.getCostPerStorage();
            costPerBandwidth = characteristics.getCostPerBw();
        }

        double executionTime = cloudlet.getActualCPUTime();

        double processingCost = executionTime * costPerSecond;

        double memoryUsage = 512.0;
        if (cloudlet.getNumberOfPes() == 2) memoryUsage = 1024.0;
        if (cloudlet.getNumberOfPes() == 4) memoryUsage = 2048.0;
        double memoryCost = executionTime * memoryUsage * costPerMemory;

        double storageUsage = 10000.0;
        double storageCost = executionTime * storageUsage * costPerStorage;

        double bandwidthUsage = 1000.0;
        double bandwidthCost = executionTime * bandwidthUsage * costPerBandwidth;

        double totalCost = processingCost + memoryCost + storageCost + bandwidthCost;

        return totalCost;
    }

    private static void printConsoleResults(List<Cloudlet> cloudlets) {
        if (cloudlets == null || cloudlets.isEmpty()) {
            Log.printLine("No results to display");
            return;
        }

        DecimalFormat df = new DecimalFormat("0.00");

        Log.printLine("\n" + "=".repeat(100));
        Log.printLine("Detailed Results (First 10 Cloudlets)");
        Log.printLine("=".repeat(100));
        Log.printLine(String.format("%-12s %-10s %-8s %-8s %-12s %-12s %-12s %-10s",
            "CloudletId", "VMId", "HostId", "Status", "StartTime", "FinishTime", "ExecutionTime", "Cost"));
        Log.printLine("-".repeat(100));

        int displayCount = Math.min(10, cloudlets.size());
        for (int i = 0; i < displayCount; i++) {
            Cloudlet cl = cloudlets.get(i);
            String status = (cl.getCloudletStatus() == Cloudlet.SUCCESS) ? "SUCCESS" : "FAILED";
            double cost = 0;
            double execTime = 0;
            double startTime = 0;
            double finishTime = 0;

            if (status.equals("SUCCESS")) {
                cost = calculateCloudletCost(cl);
                execTime = cl.getActualCPUTime();
                startTime = cl.getExecStartTime();
                finishTime = cl.getFinishTime();
            }

            Log.printLine(String.format("%-12d %-10d %-8d %-8s %-12s %-12s %-12s %-10s",
                cl.getCloudletId(),
                cl.getVmId(),
                cl.getResourceId(),
                status,
                df.format(startTime),
                df.format(finishTime),
                df.format(execTime),
                "$" + df.format(cost)));
        }

        if (cloudlets.size() > 10) {
            Log.printLine("... (" + (cloudlets.size() - 10) + " additional Cloudlets)");
        }
        Log.printLine("=".repeat(100));
    }

   private static void saveResultsToCSV(String filename) {
    // UTF-8 BOM to make Excel open Arabic/English correctly
    final String UTF8_BOM = "\uFEFF";
    try (java.io.OutputStream os = new java.io.FileOutputStream(filename);
         java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8);
         java.io.BufferedWriter writer = new java.io.BufferedWriter(osw)) {

        // write BOM once at start
        writer.write(UTF8_BOM);

        writer.write("CloudletId,VMId,HostId,StartTime,FinishTime,ExecutionTime,Cost,Status\n");

        for (String[] row : csvData) {
            writer.write(String.join(",", row));
            writer.write("\n");
        }

        Log.printLine("\nResults saved to file: " + filename);
        Log.printLine("   Records: " + csvData.size());
        Log.printLine("   Columns: CloudletId, VMId, HostId, StartTime, FinishTime, ExecutionTime, Cost, Status");

    } catch (Exception e) {
        Log.printLine("Error saving CSV file: " + e.getMessage());
    }


    }
}
