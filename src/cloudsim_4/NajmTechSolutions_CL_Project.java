
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

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
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * مشروع NajmTech Solutions لمحاكاة بيئة سحابية مبسطة باستخدام CloudSim
 *
 * المميزات: - ربط شبكة فعّال (Broker <-> Datacenter) باستخدام Entity IDs -
 * ترتيب VMs بأسلوب First-Fit (الأخف أولاً) قبل إرسالها للـ Broker - جدولة
 * Cloudlets داخل الـ VM: TimeShared - حساب تكلفة مفصلة لكل Cloudlet - حفظ
 * النتائج في CSV
 */
public class NajmTechSolutions_CL_Project {

    public static void main(String[] args) {

        Log.printLine("NajmTech Solutions - CloudSim Network & Cost Simulation");
        Log.printLine("=========================================================");

        try {
            // 1) تهيئة CloudSim
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUsers, calendar, traceFlag);

            // 2) إنشاء Datacenter
            Datacenter primaryDc = createNajmTechDatacenter("NajmTech_PrimaryDC", 0);
            datacenterList.add(primaryDc);

            // 3) إنشاء Broker مخصص لتسجيل VM->Host
            brokerRef = new NajmTechBroker("NajmTech_Broker");
            DatacenterBroker broker = brokerRef;
            int brokerId = broker.getId();

            // 4) ربط شبكة فعّال (بين Broker و Datacenter)
            configureNetworkTopology(primaryDc, broker);

            // 5) إنشاء VMs وترتيبها First-Fit ثم إرسالها للـ Broker
            Log.printLine("\n==== Creating NajmTech VMs (First-Fit order) ====");
            createVMsWithFirstFitOrder(brokerId);
            broker.submitVmList(vmList);

            // 6) إنشاء Cloudlets وإرسالها للـ Broker
            Log.printLine("\n==== Creating NajmTech Cloudlets workload (TimeShared) ====");
            createCloudletsForNajmTech(brokerId);
            broker.submitCloudletList(cloudletList);

            // 7) تشغيل المحاكاة
            Log.printLine("\n==== Starting CloudSim simulation for NajmTech ====");
            CloudSim.startSimulation();

            // الحصول على النتائج بعد انتهاء التنفيذ
            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();

            // إيقاف المحاكاة
            CloudSim.stopSimulation();

            // 8) تحليل النتائج وحساب التكاليف
            analyzeResultsAndCalculateCosts(finishedCloudlets);

            // 9) طباعة جزء من النتائج في الكونسول
            printConsoleResults(finishedCloudlets);

            // 10) حفظ النتائج إلى CSV
            saveResultsToCSV("najmtech_simulation_results.csv");

            Log.printLine("\nSimulation for NajmTech Solutions completed successfully.");
            Log.printLine("CSV file generated: najmtech_simulation_results.csv");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("An error occurred during NajmTech simulation: " + e.getMessage());
        }
    }

    // قائمة الـ Cloudlets (المهام)
    private static List<Cloudlet> cloudletList;

    // قائمة الـ VMs
    private static List<Vm> vmList;

    // مرجع الـ Broker (مهم لأننا مستخدمين Broker مخصص)
    private static DatacenterBroker brokerRef;

    // قائمة مراكز البيانات (نستخدم واحد فقط الآن)
    private static final List<Datacenter> datacenterList = new ArrayList<>();

    // صفوف CSV (سطر لكل Cloudlet)
    private static final List<String[]> csvRows = new ArrayList<>();

    // خصائص الـ Datacenter لحساب التكلفة من نفس قيم CloudSim
    private static final Map<Integer, DatacenterCharacteristics> dcCharacteristicsMap = new HashMap<>();

    /**
     * خريطة VMId -> HostId
     *
     * لماذا نحتاجها؟ - أحياناً بعد انتهاء المحاكاة قد لا تعتمد على vm.getHost()
     * (قد تكون null أو غير مضمونة). - لذلك نسجل الـ HostId لحظة نجاح إنشاء الـ
     * VM داخل processVmCreate().
     */
    private static final Map<Integer, Integer> vmToHostMap = new HashMap<>();

    /**
     * ربط شبكة فعّال باستخدام Entity IDs الحقيقية في CloudSim
     *
     * ملاحظة: CloudSim الكلاسيكي ما ينشئ Router/Switch كـ Entities جاهزة
     * تلقائياً، لذلك الربط الذي يؤثر فعلاً هو بين Broker و Datacenter.
     */
    private static void configureNetworkTopology(Datacenter dc, DatacenterBroker broker) {
        Log.printLine("\n==== Setting up NajmTech Network Topology (Entity-ID Based) ====");

        int dcId = dc.getId();
        int brId = broker.getId();

        double delayMs = 1.6;
        double bwMbps = 30.0;

        NetworkTopology.addLink(brId, dcId, delayMs, bwMbps);
        NetworkTopology.addLink(dcId, brId, delayMs, bwMbps);

        Log.printLine("Applied network links: Broker(" + brId + ") <-> Datacenter(" + dcId
                + "), delay=" + delayMs + "ms, bw=" + bwMbps + "Mbps");
    }

    /**
     * إنشاء Datacenter خاص بـ NajmTech
     */
    private static Datacenter createNajmTechDatacenter(String name, int idAlias) {
        try {
            List<Host> hostList = new ArrayList<>();

            Log.printLine("\n==== Creating NajmTech Datacenter Hosts ====");

            // إنشاء 4 Hosts
            for (int i = 0; i < 4; i++) {

                List<Pe> peList = new ArrayList<>();

                // PEs: 4,6,8,10
                int pesNumber = 4 + (i * 2);

                // MIPS: 1200,1800,2400,3000
                int mipsPerPe = 1200 + (i * 600);

                for (int peId = 0; peId < pesNumber; peId++) {
                    peList.add(new Pe(peId, new PeProvisionerSimple(mipsPerPe)));
                }

                // RAM: 8GB,12GB,16GB,20GB
                int ramMb = 8192 + (i * 4096);

                // Storage: 750GB, 1000GB, 1250GB, 1500GB (تقريباً بالـ MB)
                long storageMb = 750000L + (i * 250000L);

                // BW: 7000,11000,15000,19000 Mbps
                int bwMbps = 7000 + (i * 4000);

                Host host = new Host(
                        idAlias * 10 + i,
                        new RamProvisionerSimple(ramMb),
                        new BwProvisionerSimple(bwMbps),
                        storageMb,
                        peList,
                        // TimeShared على مستوى الـ Host لتوزيع المعالج بين الـ VMs
                        new VmSchedulerTimeShared(peList)
                );

                hostList.add(host);

                Log.printLine("   Host " + (idAlias * 10 + i) + ": "
                        + pesNumber + "x" + mipsPerPe + " MIPS, "
                        + ramMb + "MB RAM, "
                        + (storageMb / 1000) + "GB Storage, "
                        + bwMbps + "Mbps BW");
            }

            // نموذج التكلفة
            double costPerSecond = 0.12;
            double costPerMemory = 0.000025;
            double costPerStorage = 0.0000015;
            double costPerBandwidth = 0.00006;

            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                    "x86", "Linux", "Xen", hostList, 3.0,
                    costPerSecond, costPerMemory, costPerStorage, costPerBandwidth
            );

            Datacenter datacenter = new Datacenter(
                    name,
                    characteristics,
                    // سياسة توزيع VMs على Hosts (أساسية)
                    new VmAllocationPolicySimple(hostList),
                    new LinkedList<Storage>(),
                    0
            );

            dcCharacteristicsMap.put(datacenter.getId(), characteristics);
            return datacenter;

        } catch (Exception e) {
            Log.printLine("Error while creating NajmTech Datacenter: " + e.getMessage());
            return null;
        }
    }

    /**
     * إنشاء VMs وترتيبها First-Fit (الأخف أولاً)
     *
     * ملاحظة: هذا ترتيب قبل الإرسال للـ Broker فقط. التخصيص الفعلي على Host يتم
     * عبر VmAllocationPolicySimple داخل الـ Datacenter.
     */
    private static void createVMsWithFirstFitOrder(int brokerId) {

        vmList = new ArrayList<>();

        String[] vmTypes = {"EdgeNano", "EdgePro", "EdgeUltra"};

        // [mips, ramMB, bw, sizeMB, pes]
        int[][] vmConfigs = {
            {600, 1024, 1500, 8000, 1},
            {1400, 2048, 2500, 16000, 2},
            {2600, 4096, 4500, 32000, 4}
        };

        int vmId = 0;

        // 3 نسخ من كل نوع = 9 VMs
        for (int typeIndex = 0; typeIndex < vmConfigs.length; typeIndex++) {
            for (int copy = 0; copy < 3; copy++) {

                Vm vm = new Vm(
                        vmId,
                        brokerId,
                        vmConfigs[typeIndex][0], // MIPS
                        vmConfigs[typeIndex][4], // vCPUs
                        vmConfigs[typeIndex][1], // RAM
                        vmConfigs[typeIndex][2], // BW
                        vmConfigs[typeIndex][3], // Size
                        "Xen",
                        // TimeShared داخل الـ VM: يسمح بتشارك الـ CPU بين Cloudlets
                        new CloudletSchedulerTimeShared()
                );

                vmList.add(vm);
                vmId++;
            }

            Log.printLine("   " + vmTypes[typeIndex] + " VM: "
                    + vmConfigs[typeIndex][0] + " MIPS, "
                    + vmConfigs[typeIndex][1] + "MB RAM, "
                    + vmConfigs[typeIndex][3] + "MB Storage, "
                    + vmConfigs[typeIndex][4] + " vCPUs, "
                    + vmConfigs[typeIndex][2] + " Mbps BW");
        }

        // First-Fit: ترتيب تصاعدي حسب (MIPS*PEs + RAM)
        Collections.sort(vmList, new Comparator<Vm>() {
            @Override
            public int compare(Vm v1, Vm v2) {
                double r1 = v1.getMips() * v1.getNumberOfPes() + v1.getRam();
                double r2 = v2.getMips() * v2.getNumberOfPes() + v2.getRam();
                return Double.compare(r1, r2);
            }
        });

        Log.printLine("Total VMs created for NajmTech: " + vmList.size());
        Log.printLine("VMs ordered using First-Fit style (small → large).");
    }

    /**
     * إنشاء 20 Cloudlets: - 6 خفيفة (1 PE) - 7 متوسطة (2 PEs) - 7 ثقيلة (4 PEs)
     */
    private static void createCloudletsForNajmTech(int brokerId) {

        cloudletList = new ArrayList<>();

        // UtilizationModelFull = استهلاك كامل (100%)
        UtilizationModel utilizationModel = new UtilizationModelFull();

        int totalCloudlets = 20;

        for (int i = 0; i < totalCloudlets; i++) {

            long length;
            int pesNumber;
            long fileSize;
            long outputSize;

            if (i < 6) {
                length = 12000 + (i * 800);
                pesNumber = 1;
            } else if (i < 13) {
                length = 26000 + ((i - 6) * 1600);
                pesNumber = 2;
            } else {
                length = 52000 + ((i - 13) * 2200);
                pesNumber = 4;
            }

            fileSize = 400 + (i * 45);
            outputSize = 280 + (i * 30);

            Cloudlet cloudlet = new Cloudlet(
                    i,
                    length,
                    pesNumber,
                    fileSize,
                    outputSize,
                    utilizationModel,
                    utilizationModel,
                    utilizationModel
            );

            cloudlet.setUserId(brokerId);
            cloudletList.add(cloudlet);
        }

        Log.printLine("Created " + cloudletList.size() + " Cloudlets for NajmTech workload");
        Log.printLine("   - Scheduling policy inside VMs: TimeShared");
    }

    /**
     * إرجاع HostId للـ Cloudlet اعتماداً على الخريطة VM->Host المسجلة وقت إنشاء
     * الـ VM
     */
    private static int getHostIdForCloudlet(Cloudlet cl) {
        Integer hostId = vmToHostMap.get(cl.getVmId());
        return (hostId == null) ? -1 : hostId;
    }

    /**
     * البحث عن VM حسب ID (مفيد لحساب Utilization)
     */
    private static Vm findVmById(int vmId) {
        if (brokerRef != null) {
            List<Vm> created = brokerRef.getVmsCreatedList();
            if (created != null) {
                for (Vm vm : created) {
                    if (vm.getId() == vmId) {
                        return vm;
                    }
                }
            }
        }
        if (vmList != null) {
            for (Vm vm : vmList) {
                if (vm.getId() == vmId) {
                    return vm;
                }
            }
        }
        return null;
    }

    /**
     * تحليل النتائج وحساب: - Execution Time - Waiting Time (نعتمده = StartTime)
     * - CPU Utilization (تقريبي) - Cost
     */
    private static void analyzeResultsAndCalculateCosts(List<Cloudlet> cloudlets) {

        if (cloudlets == null || cloudlets.isEmpty()) {
            Log.printLine("No cloudlets were executed. Nothing to analyze.");
            return;
        }

        DecimalFormat df = new DecimalFormat("0.00");

        double totalCost = 0.0;
        double totalExecutionTime = 0.0;
        double totalWaitingTime = 0.0;
        double totalCpuUtil = 0.0;
        double maxCpuUtil = 0.0;

        int successCount = 0;

        Log.printLine("\n==== NajmTech – Results Analysis & Cost Calculation ====");

        for (Cloudlet cloudlet : cloudlets) {

            String status = (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) ? "SUCCESS" : "FAILED";

            double cost = 0.0;
            double execTime = 0.0;
            double startTime = 0.0;
            double finishTime = 0.0;
            double cpuUtilPercent = 0.0;

            if ("SUCCESS".equals(status)) {
                successCount++;

                execTime = cloudlet.getActualCPUTime();
                startTime = cloudlet.getExecStartTime();
                finishTime = cloudlet.getFinishTime();

                cost = calculateCloudletCost(cloudlet);

                totalCost += cost;
                totalExecutionTime += execTime;
                totalWaitingTime += startTime;

                // حساب Utilization تقريبي: effectiveMips / vmTotalMips
                Vm vm = findVmById(cloudlet.getVmId());
                if (vm != null && execTime > 0) {
                    double cloudletLength = cloudlet.getCloudletLength();
                    double vmTotalMips = vm.getMips() * vm.getNumberOfPes();
                    if (vmTotalMips > 0) {
                        double effectiveMips = cloudletLength / execTime;
                        cpuUtilPercent = (effectiveMips / vmTotalMips) * 100.0;
                        totalCpuUtil += cpuUtilPercent;
                        maxCpuUtil = Math.max(maxCpuUtil, cpuUtilPercent);
                    }
                }
            }

            csvRows.add(new String[]{
                String.valueOf(cloudlet.getCloudletId()),
                String.valueOf(cloudlet.getVmId()),
                String.valueOf(getHostIdForCloudlet(cloudlet)),
                df.format(startTime),
                df.format(finishTime),
                df.format(execTime),
                df.format(cost),
                status
            });
        }

        Log.printLine("\nPerformance Statistics (NajmTech):");
        Log.printLine("   - Successful Cloudlets : " + successCount + " / " + cloudlets.size());

        if (successCount > 0) {
            Log.printLine("   - Success Rate         : " + df.format((successCount * 100.0) / cloudlets.size()) + " %");
            Log.printLine("   - Total Execution Time : " + df.format(totalExecutionTime) + " sec");
            Log.printLine("   - Avg. Execution Time  : " + df.format(totalExecutionTime / successCount) + " sec");
            Log.printLine("   - Total Waiting Time   : " + df.format(totalWaitingTime) + " sec");
            Log.printLine("   - Avg. Waiting Time    : " + df.format(totalWaitingTime / successCount) + " sec");
            Log.printLine("   - Total Cost           : $" + df.format(totalCost));
            Log.printLine("   - Avg. Cost / Cloudlet : $" + df.format(totalCost / successCount));

            if (totalCpuUtil > 0) {
                Log.printLine("   - Avg. CPU Utilization : " + df.format(totalCpuUtil / successCount) + " %");
                Log.printLine("   - Max  CPU Utilization : " + df.format(maxCpuUtil) + " %");
            }
        }
    }

    /**
     * حساب تكلفة Cloudlet بناءً على معادلات التكلفة في
     * DatacenterCharacteristics
     */
    private static double calculateCloudletCost(Cloudlet cloudlet) {

        DatacenterCharacteristics characteristics = dcCharacteristicsMap.get(cloudlet.getResourceId());

        // قيم افتراضية احتياطية
        double costPerSecond = 0.12;
        double costPerMemory = 0.000025;
        double costPerStorage = 0.0000015;
        double costPerBandwidth = 0.00006;

        if (characteristics != null) {
            costPerSecond = characteristics.getCostPerSecond();
            costPerMemory = characteristics.getCostPerMem();
            costPerStorage = characteristics.getCostPerStorage();
            costPerBandwidth = characteristics.getCostPerBw();
        }

        double executionTime = cloudlet.getActualCPUTime();

        // 1) Processing Cost
        double processingCost = executionTime * costPerSecond;

        // 2) Memory Cost (تقدير مبسط حسب عدد PEs)
        double memoryUsageMb = 768.0;
        int pes = cloudlet.getNumberOfPes();
        if (pes == 2) {
            memoryUsageMb = 1536.0;
        } else if (pes >= 4) {
            memoryUsageMb = 3072.0;
        }
        double memoryCost = executionTime * memoryUsageMb * costPerMemory;

        // 3) Storage Cost
        double storageUsageMb = cloudlet.getCloudletFileSize() + cloudlet.getCloudletOutputSize();
        if (storageUsageMb <= 0) {
            storageUsageMb = 12000.0;
        }
        double storageCost = executionTime * storageUsageMb * costPerStorage;

        // 4) Bandwidth Cost (تقدير ثابت)
        double bandwidthUsageMbps = 1200.0;
        double bandwidthCost = executionTime * bandwidthUsageMbps * costPerBandwidth;

        return processingCost + memoryCost + storageCost + bandwidthCost;
    }

    /**
     * طباعة أول 10 Cloudlets في الكونسول
     */
    private static void printConsoleResults(List<Cloudlet> cloudlets) {

        if (cloudlets == null || cloudlets.isEmpty()) {
            Log.printLine("No cloudlets to display.");
            return;
        }

        DecimalFormat df = new DecimalFormat("0.00");

        Log.printLine("\n" + "=".repeat(110));
        Log.printLine("NajmTech – Detailed Results (First 10 Cloudlets)");
        Log.printLine("=".repeat(110));

        Log.printLine(String.format(
                "%-12s %-10s %-8s %-10s %-12s %-12s %-14s %-10s",
                "CloudletId", "VMId", "HostId", "Status",
                "StartTime", "FinishTime", "ExecTime", "Cost"));

        Log.printLine("-".repeat(110));

        int displayCount = Math.min(10, cloudlets.size());

        for (int i = 0; i < displayCount; i++) {
            Cloudlet cl = cloudlets.get(i);

            String status = (cl.getCloudletStatus() == Cloudlet.SUCCESS) ? "SUCCESS" : "FAILED";

            double startTime = 0.0;
            double finishTime = 0.0;
            double execTime = 0.0;
            double cost = 0.0;

            if ("SUCCESS".equals(status)) {
                startTime = cl.getExecStartTime();
                finishTime = cl.getFinishTime();
                execTime = cl.getActualCPUTime();
                cost = calculateCloudletCost(cl);
            }

            Log.printLine(String.format(
                    "%-12d %-10d %-8d %-10s %-12s %-12s %-14s $%-9s",
                    cl.getCloudletId(),
                    cl.getVmId(),
                    getHostIdForCloudlet(cl),
                    status,
                    df.format(startTime),
                    df.format(finishTime),
                    df.format(execTime),
                    df.format(cost)));
        }

        if (cloudlets.size() > 10) {
            Log.printLine("... (" + (cloudlets.size() - 10) + " additional Cloudlets not shown)");
        }

        Log.printLine("=".repeat(110));
    }

    /**
     * حفظ النتائج في CSV مع BOM لدعم العربية في Excel
     */
    private static void saveResultsToCSV(String filename) {

        final String UTF8_BOM = "\uFEFF";

        // ترتيب تنازلي حسب CloudletId
        Collections.sort(csvRows, new Comparator<String[]>() {
            @Override
            public int compare(String[] r1, String[] r2) {
                int id1 = Integer.parseInt(r1[0]);
                int id2 = Integer.parseInt(r2[0]);
                return Integer.compare(id2, id1);
            }
        });

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), java.nio.charset.StandardCharsets.UTF_8))) {

            writer.write(UTF8_BOM);
            writer.write("CloudletId,VMId,HostId,StartTime,FinishTime,ExecutionTime,Cost,Status\n");

            for (String[] row : csvRows) {
                writer.write(String.join(",", row));
                writer.newLine();
            }

            Log.printLine("\nNajmTech – results saved to file: " + filename);
            Log.printLine("   Records: " + csvRows.size());

        } catch (Exception e) {
            Log.printLine("Error while saving CSV file: " + e.getMessage());
        }
    }

    /**
     * Broker مخصص: يسجل VMId -> HostId عند نجاح إنشاء الـ VM
     */
    public static class NajmTechBroker extends DatacenterBroker {

        // لتفادي إعادة تسجيل نفس الـ VM
        private int lastRecordedCount = 0;

        public NajmTechBroker(String name) throws Exception {
            super(name);
        }

        @Override
        protected void processVmCreate(SimEvent ev) {
            // نفّذ السلوك الأصلي (إنشاء VM)
            super.processVmCreate(ev);

            // بعد التنفيذ: أي VM تم إنشاؤها ستظهر في getVmsCreatedList()
            List<Vm> created = getVmsCreatedList();
            if (created == null) {
                return;
            }

            // سجل فقط الـ VMs الجديدة
            for (int i = lastRecordedCount; i < created.size(); i++) {
                Vm vm = created.get(i);
                if (vm != null && vm.getHost() != null) {
                    vmToHostMap.put(vm.getId(), vm.getHost().getId());
                }
            }
            lastRecordedCount = created.size();
        }
    }
}
