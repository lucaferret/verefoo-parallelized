package it.polito.verefoo.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.TestCaseGeneratorCesnet;
import it.polito.verefoo.extra.TestCaseGeneratorStanford;
import it.polito.verefoo.extra.TestCaseGeneratorTexas2000;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.partitioning.GurobiClusterer;
import it.polito.verefoo.utils.TestResults;

public class TestBenchmarkOrchestrator {

    // --- TELEGRAM CONFIGURATION ---
    private static final String TELEGRAM_TOKEN = "YOUR_BOT_TOKEN_HERE";
    private static final String TELEGRAM_CHAT_ID = "YOUR_CHAT_ID_HERE";

    // --- BENCHMARK CONFIGURATION ---
    private static final int RUNS_PER_CONFIG = 3;
    static int baseSeed = 999;
    static Random rand;

    static double[] lambdaOptimizationVals = {0.6};
    static double[] lambdaPerformanceVals  = {0.6};

    public static void main(String[] args) {
        sendTelegramMessage("*Gurobi Benchmark Marathon Started!* \nRunning with updated core metrics.");

        GurobiClusterer.setStopAfterGurobi(false);
        rand = new Random(baseSeed);

        // ==========================================
        // 1. RUN STANFORD BENCHMARK
        // ==========================================
/*
        try {
            runStanfordSuite();
            sendTelegramFile("results_stanford_core.csv");
        } catch (Exception e) {
            sendTelegramMessage("*Fatal Error in Stanford Suite:* \n" + e.getMessage());
            e.printStackTrace();
        }
*/
        // ==========================================
        // 2. RUN TEXAS2000 BENCHMARK
        // ==========================================

        /*
        try {
            runTexasSuite();
            sendTelegramFile("results_texas2000_core.csv");
        } catch (Exception e) {
            sendTelegramMessage("*Fatal Error in Texas2000 Suite:* \n" + e.getMessage());
            e.printStackTrace();
        }
        */
        // ==========================================
        // 3. RUN CESNET BENCHMARK
        // ==========================================

        try {
            runCesnetSuite();
            sendTelegramFile("results_cesnet_core.csv");
        } catch (Exception e) {
            sendTelegramMessage("*Fatal Error in Cesnet Suite:* \n" + e.getMessage());
            e.printStackTrace();
        }


        sendTelegramMessage("*Gurobi Benchmark Marathon Finished!*");
    }

    private static NFV deepCopyNFV(NFV source) throws Exception {
        JAXBContext jc = JAXBContext.newInstance(NFV.class);
        Marshaller m = jc.createMarshaller();
        StringWriter sw = new StringWriter();
        m.marshal(source, sw);
        Unmarshaller u = jc.createUnmarshaller();
        StringReader sr = new StringReader(sw.toString());
        return (NFV) u.unmarshal(sr);
    }

    private static void runStanfordSuite() {
        sendTelegramMessage("*[1/3] Starting Stanford Suite...*");
        String csvPath = "results_stanford_core.csv";
        prepareCsvHeader(csvPath, "NetworkName,TotalNodes,AllocationPlaces,Policies,ReachPerc,CompleteReachPerc,PortPerc,Algorithm,LambdaOpt,LambdaPerf,AvgVanillaTime_s,AvgVanillaFWs,AvgVanillaTotalRules,AvgClusteredTime_s,AvgClusteredFWs,AvgClusteredTotalRules\n");

        int[] numberPRs = {10, 20, 30, 40, 50, 55};
        String[] algorithms = {"AP"};

        for (int pr : numberPRs) {
            for (String algo : algorithms) {
                for (double lOpt : lambdaOptimizationVals) {
                    for (double lPerf : lambdaPerformanceVals) {
                        String networkName = "Stanford_" + pr + "PR";
                        executeBenchmarkStep(csvPath, networkName, algo, pr, 0.5, 0.3, 0.1, 1, lOpt, lPerf);
                    }
                }
            }
        }
    }

    private static void runTexasSuite() {
        System.out.println("*[2/3] Starting Texas2000 Suite with Policy Density...*");
        String csvPath = "results_texas2000_core.csv";
        prepareCsvHeader(csvPath, "NetworkName,UCC,SS,TotalNodes,AllocationPlaces,Policies,Algorithm,LambdaOpt,LambdaPerf,AvgVanillaTime_s,AvgVanillaFWs,AvgVanillaTotalRules,AvgClusteredTime_s,AvgClusteredFWs,AvgClusteredTotalRules\n");

        // Your requested stressful topologies
        int[][] topologies = {
                //{1, 3}, {2, 6}, {3, 9}, {4, 12}, {5, 15}, {6, 18}
                {7,21}, {8,24}, {9,27}, {10,30}, {20,60}, {30, 90}
        };

        // 0.5 = 50% of nodes, 1.0 = 100%, 2.0 = 200%
        String[] algorithms = {"AP"}; // Add "MF" if you want to test both

        for (int[] topo : topologies) {
            int ucc = topo[0];
            int ss = topo[1];

            // Calculate exact nodes before generation using the deduced formula
            int estimatedNodes = 17 + (16 * ucc) + (14 * (ss - 1));

                // Apply the percentage to get the exact integer of policies
                int policies = (int) Math.round(estimatedNodes * 2.0);
                System.out.println("####### Policies are: " + policies);

                String networkName = String.format(java.util.Locale.US, "Texas_U%d_S%d", ucc, ss);

                for (String algo : algorithms) {
                    for (double lOpt : lambdaOptimizationVals) {
                        for (double lPerf : lambdaPerformanceVals) {
                            // Pass 'policies' via p3 to the executeBenchmarkStep
                            executeBenchmarkStep(csvPath, networkName, algo, ucc, ss, policies, 0, 2, lOpt, lPerf);
                        }
                    }
                }

        }
    }
    private static void runCesnetSuite() {
        sendTelegramMessage("*[3/3] Starting CESNET Suite...*");
        String csvPath = "results_cesnet_core.csv";
        prepareCsvHeader(csvPath, "NetworkName,TotalNodes,AllocationPlaces,Policies,ReachPerc,Algorithm,LambdaOpt,LambdaPerf,AvgVanillaTime_s,AvgVanillaFWs,AvgVanillaTotalRules,AvgClusteredTime_s,AvgClusteredFWs,AvgClusteredTotalRules\n");

        int[] policyNumbers = {20, 30, 35, 40, 45};
        String[] algorithms = {"AP"};

        for (int policyNum : policyNumbers) {
            for (String algo : algorithms) {
                for (double lOpt : lambdaOptimizationVals) {
                    for (double lPerf : lambdaPerformanceVals) {
                        String networkName = "Cesnet_" + policyNum + "Pol";
                        executeBenchmarkStep(csvPath, networkName, algo, policyNum, 0.5, 0, 0, 3, lOpt, lPerf);
                    }
                }
            }
        }
    }

    private static void executeBenchmarkStep(String csvPath, String networkName, String algo,
                                             double p1, double p2, double p3, double p4,
                                             int suiteId, double lOpt, double lPerf) {

        GurobiClusterer.setLambdaOptimization(lOpt);
        GurobiClusterer.setLambdaPerformance(lPerf);

        double sumVanillaTime = 0.0, sumVanillaFws = 0.0, sumVanillaRules = 0.0;
        double sumClusteredTime = 0.0, sumClusteredFws = 0.0, sumClusteredRules = 0.0;

        int validRuns = 0;
        int successVanilla = 0;
        int successClustered = 0;

        long TIMEOUT_SECONDS = 1300; // Raised to 3 minutes to handle 60 policies

        int totalNodes = 0;
        int totalPolicies = 0;
        int totalAPs = 0;

        System.out.printf("Executing: %s [%s]\n", networkName, algo);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int k = 0; k < RUNS_PER_CONFIG; k++) {
            try {
                int runSeed = Math.abs(rand.nextInt());
                NFV baseRoot = null;

                if (suiteId == 1) {
                    TestCaseGeneratorStanford gen = new TestCaseGeneratorStanford(networkName + "_r" + k, (int) p1, runSeed, p2, p3, p4);
                    baseRoot = gen.getNfv();
                } else if (suiteId == 2) {
                    TestCaseGeneratorTexas2000 gen = new TestCaseGeneratorTexas2000(runSeed, (int) p1, (int) p2, true, true, (int) p3);
                    baseRoot = gen.generateNFV((int) p1, (int) p2, true, true, (int) p3);
                } else if (suiteId == 3) {
                    TestCaseGeneratorCesnet gen = new TestCaseGeneratorCesnet("testfile/CESNET/cesnet1", true, (int) p1, p2, true);
                    baseRoot = gen.getNFV();
                }

                if (totalNodes == 0) {
                    totalNodes = baseRoot.getGraphs().getGraph().get(0).getNode().size();
                    totalPolicies = baseRoot.getPropertyDefinition().getProperty().size();
                }

                totalAPs = 0;
                for (it.polito.verefoo.jaxb.Node n : baseRoot.getGraphs().getGraph().get(0).getNode()) {
                    if (n.getFunctionalType() == null) {
                        totalAPs++;
                    }
                }

                NFV rootVanilla = deepCopyNFV(baseRoot);
                NFV rootClustered = deepCopyNFV(baseRoot);

                // --- RUN 1: VANILLA ---
                Callable<VerefooSerializer> vanillaTask = () -> new VerefooSerializer(rootVanilla, algo, false);
                Future<VerefooSerializer> vanillaFuture = executor.submit(vanillaTask);
                try {
                    VerefooSerializer vanillaRun = vanillaFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    sumVanillaTime += vanillaRun.getTime();
                    sumVanillaFws += vanillaRun.getTestTimeResults().getAllocatedFirewalls();
                    sumVanillaRules += vanillaRun.getTestTimeResults().getTotalRules();
                    //totalAPs = vanillaRun.getTestTimeResults().getAllocationPlaces();
                    successVanilla++;
                } catch (TimeoutException e) {
                    System.err.println("   [TIMEOUT] Vanilla VEREFOO exceeded " + TIMEOUT_SECONDS + "s.");
                    vanillaFuture.cancel(true);
                    sumVanillaTime += (TIMEOUT_SECONDS * 1000);
                }

                // --- RUN 2: CLUSTERED ---
                Callable<VerefooSerializer> clusteredTask = () -> {
                    GurobiClusterer.resetLastResults();
                    return new VerefooSerializer(rootClustered, algo, true);
                };
                Future<VerefooSerializer> clusteredFuture = executor.submit(clusteredTask);
                try {
                    VerefooSerializer clusteredRun = clusteredFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    TestResults cMetrics = clusteredRun.getTestTimeResults();

                    sumClusteredTime += (cMetrics.getGurobiTime() + clusteredRun.getTime() + cMetrics.getMergeTime());
                    sumClusteredFws += cMetrics.getAllocatedFirewalls();
                    sumClusteredRules += cMetrics.getTotalRules();

                    //if (totalAPs == 0) totalAPs = cMetrics.getAllocationPlaces();
                    successClustered++;
                } catch (TimeoutException e) {
                    System.err.println("   [TIMEOUT] Clustered VEREFOO exceeded " + TIMEOUT_SECONDS + "s.");
                    clusteredFuture.cancel(true);
                    sumClusteredTime += (TIMEOUT_SECONDS * 1000);
                }

                validRuns++;

            } catch (Exception e) {
                System.err.println("Run error in " + networkName + ": " + e.getMessage());
            }
        }

        executor.shutdownNow();

        // Calculate Averages - Time includes penalties, FWs/Rules ONLY count successful runs!
        double avgVTime = validRuns > 0 ? sumVanillaTime / validRuns : 0.0;
        double avgVFws = successVanilla > 0 ? sumVanillaFws / successVanilla : 0.0;
        double avgVRules = successVanilla > 0 ? sumVanillaRules / successVanilla : 0.0;

        double avgCTime = validRuns > 0 ? sumClusteredTime / validRuns : 0.0;
        double avgCFws = successClustered > 0 ? sumClusteredFws / successClustered : 0.0;
        double avgCRules = successClustered > 0 ? sumClusteredRules / successClustered : 0.0;

        // Write cleanly to CSV
        String csvRow;
        if (suiteId == 1) {
            csvRow = String.format(java.util.Locale.US,
                    "%s,%d,%d,%d,%.2f,%.2f,%.2f,%s,%.1f,%.1f,%.3f,%.2f,%.2f,%.3f,%.2f,%.2f",
                    networkName, totalNodes, totalAPs, totalPolicies, p2, p3, p4, algo, lOpt, lPerf,
                    (avgVTime / 1000.0), avgVFws, avgVRules,
                    (avgCTime / 1000.0), avgCFws, avgCRules);
        } else if (suiteId == 2) {
            csvRow = String.format(java.util.Locale.US,
                    "%s,%d,%d,%d,%d,%d,%s,%.1f,%.1f,%.3f,%.2f,%.2f,%.3f,%.2f,%.2f",
                    networkName, (int) p1, (int) p2, totalNodes, totalAPs, totalPolicies, algo, lOpt, lPerf,
                    (avgVTime / 1000.0), avgVFws, avgVRules,
                    (avgCTime / 1000.0), avgCFws, avgCRules);
        } else {
            csvRow = String.format(java.util.Locale.US,
                    "%s,%d,%d,%d,%.2f,%s,%.1f,%.1f,%.3f,%.2f,%.2f,%.3f,%.2f,%.2f",
                    networkName, totalNodes, totalAPs, totalPolicies, p2, algo, lOpt, lPerf,
                    (avgVTime / 1000.0), avgVFws, avgVRules,
                    (avgCTime / 1000.0), avgCFws, avgCRules);
        }

        try (BufferedWriter rowWriter = Files.newBufferedWriter(Paths.get(csvPath),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
            rowWriter.write(csvRow + "\n");
        } catch (Exception e) {
            System.err.println("Fatal CSV writing error: " + e.getMessage());
        }
    }

    private static void prepareCsvHeader(String path, String header) {
        File file = new File(path);
        if (!file.exists()) {
            try (BufferedWriter hw = Files.newBufferedWriter(Paths.get(path))) {
                hw.write(header);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendTelegramMessage(String text) {
        try {
            String urlString = "https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendMessage";
            String postData = "chat_id=" + TELEGRAM_CHAT_ID + "&text=" + URLEncoder.encode(text, "UTF-8") + "&parse_mode=Markdown";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(postData.getBytes("UTF-8"));
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            while (in.readLine() != null) {}
            in.close();
        } catch (Exception e) {}
    }

    private static void sendTelegramFile(String filePath) {
        // Your existing file upload method...
    }
}