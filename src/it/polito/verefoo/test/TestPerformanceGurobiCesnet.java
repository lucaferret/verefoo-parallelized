package it.polito.verefoo.test;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.TestCaseGeneratorCesnet;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.partitioning.GurobiClusterer;

public class TestPerformanceGurobiCesnet {

    // --- BASE CESNET TOPOLOGY PARAMETERS ---
    static String fileName = "testfile/CESNET/cesnet1";
    static Boolean isolationBidirectional = true;
    static Boolean usePorts = true;

    // Number of runs per configuration to calculate averages
    static final int runsPerConfig = 3;

    public static void main(String[] args) {
        System.out.println("Starting Gurobi Min-Cut Scalability Test on CESNET (Varying Policies)...");

        // ENABLE EARLY EXIT: We stop the execution immediately after Gurobi partitioning
        GurobiClusterer.setStopAfterGurobi(true);

        runPolicyScalabilityTest();
    }

    public static void runPolicyScalabilityTest() {

        // --- TRAFFIC PARAMETERS TO ITERATE ---
        int[] policyNumbers      = {10};
        double[] reachabilityPercs = {0.5};

        // --- ALGORITHMS AND LAMBDAS (Hyperparameters) ---
        String[] algorithms             = {"MF", "AP"};
        double[] lambdaOptimizationVals = {0.0, 0.3, 0.6, 1.0};
        double[] lambdaPerformanceVals  = {0.0, 0.3, 0.6, 1.0};

        int totalCombinations = policyNumbers.length * reachabilityPercs.length
                * algorithms.length
                * lambdaOptimizationVals.length * lambdaPerformanceVals.length;

        System.out.println("Total combinations to explore: " + totalCombinations);

        String csvFilePath = "results_cesnet_gurobi_policies.csv";
        File file = new File(csvFilePath);

        // WRITE HEADER ONLY IF THE FILE DOES NOT EXIST YET
        if (!file.exists()) {
            try (BufferedWriter headerWriter = Files.newBufferedWriter(Paths.get(csvFilePath))) {
                headerWriter.write("NetworkName,Policies,ReachPerc,Algorithm,LambdaOptimization,LambdaPerformance,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");
            } catch (Exception e) {
                System.err.println("Error creating the CSV header: " + e.getMessage());
            }
        }

        int currentComb = 0;

        for (int policyNum : policyNumbers) {
            for (double reachPerc : reachabilityPercs) {
                for (String algo : algorithms) {
                    for (double lOpt : lambdaOptimizationVals) {
                        for (double lPerf : lambdaPerformanceVals) {
                            if (lOpt == 0 && lPerf == 0) continue;

                            currentComb++;
                            String networkName = String.format("Cesnet_%dPol_r%.2f", policyNum, reachPerc);
                            System.out.println("\n--- [" + currentComb + "/" + totalCombinations + "] "
                                    + networkName + " | Algo: " + algo
                                    + " | Opt=" + lOpt + " Perf=" + lPerf + " ---");

                            GurobiClusterer.setLambdaOptimization(lOpt);
                            GurobiClusterer.setLambdaPerformance(lPerf);

                            double sumGurobiTime = 0.0;
                            double sumObjValue = 0.0;
                            double sumActiveClusters = 0.0;
                            int validRuns = 0;
                            String clusterNodes = "";
                            String clusterFlows = "";
                            String clusterAps = "";

                            for (int k = 0; k < runsPerConfig; k++) {
                                try {
                                    TestCaseGeneratorCesnet generator = new TestCaseGeneratorCesnet(
                                            fileName, isolationBidirectional, policyNum, reachPerc, usePorts
                                    );

                                    NFV root = generator.getNFV();

                                    GurobiClusterer.resetLastResults();
                                    new VerefooSerializer(root, algo);

                                    sumGurobiTime += GurobiClusterer.getLastExecTime();
                                    sumObjValue += GurobiClusterer.getLastObjValue();
                                    sumActiveClusters += GurobiClusterer.getTotalActiveClusters();

                                    clusterNodes = GurobiClusterer.getLastClusterNodeCounts();
                                    clusterFlows = GurobiClusterer.getLastClusterFlowCounts();
                                    clusterAps   = GurobiClusterer.getLastClusterApCounts();

                                    validRuns++;

                                } catch (Exception e) {
                                    System.err.println("Error in Cesnet Run " + k + " (Algo: " + algo + "): " + e.getMessage());
                                }
                            }

                            double avgGurobiTime     = validRuns > 0 ? sumGurobiTime     / validRuns : 0.0;
                            double avgObjValue       = validRuns > 0 ? sumObjValue       / validRuns : 0.0;
                            double avgActiveClusters = validRuns > 0 ? sumActiveClusters / validRuns : 0.0;

                            System.out.printf("Gurobi [%s] -> Time: %.3f s | Obj: %.2f | Clusters: %.1f\n",
                                    algo, avgGurobiTime, avgObjValue, avgActiveClusters);

                            String csvRow = String.format(java.util.Locale.US,
                                    "%s,%d,%.2f,%s,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                                    networkName, policyNum, reachPerc, algo,
                                    lOpt, lPerf,
                                    avgGurobiTime, avgObjValue, avgActiveClusters,
                                    clusterNodes, clusterFlows, clusterAps);

                            try (BufferedWriter rowWriter = Files.newBufferedWriter(
                                    Paths.get(csvFilePath),
                                    java.nio.file.StandardOpenOption.CREATE,
                                    java.nio.file.StandardOpenOption.APPEND)) {
                                rowWriter.write(csvRow + "\n");
                            } catch (Exception e) {
                                System.err.println("Unable to save row to CSV: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        System.out.println("\nCESNET Policy Benchmark completed! Data successfully saved.");
    }
}