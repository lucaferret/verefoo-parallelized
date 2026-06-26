package it.polito.verefoo.test;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.TestCaseGeneratorStanford;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.partitioning.GurobiClusterer;

public class TestPerformanceGurobiStanford {

    static String prefix = "Stanford";
    static int baseSeed = 999;
    static Random rand;

    // Number of runs per configuration to calculate averages
    static final int runsPerConfig = 3;

    public static void main(String[] args) {
        System.out.println("Starting Gurobi Min-Cut Scalability Test on Stanford (Varying Policies)...");

        // ENABLE EARLY EXIT: We stop the execution immediately after Gurobi partitioning
        GurobiClusterer.setStopAfterGurobi(true);

        runPolicyScalabilityTest();
    }

    public static void runPolicyScalabilityTest() {
        rand = new Random(baseSeed);

        // --- TOPOLOGY AND TRAFFIC PARAMETERS ---
        int[] numberPRs = {200};

        double[][] percentPairs = {
                {0.5, 0.3}
        };

        double[] portSpecifics = {0.1};

        // --- ALGORITHMS AND LAMBDAS (Hyperparameters) ---
        String[] algorithms             = {"MF", "AP"};
        double[] lambdaOptimizationVals = {0.0, 0.3, 0.6, 1.0};
        double[] lambdaPerformanceVals  = {0.0, 0.3, 0.6, 1.0};

        int totalCombinations = numberPRs.length * percentPairs.length * portSpecifics.length
                * algorithms.length
                * lambdaOptimizationVals.length * lambdaPerformanceVals.length;

        System.out.println("Total combinations to explore: " + totalCombinations);

        String csvFilePath = "results_stanford_gurobi_policies.csv";
        File file = new File(csvFilePath);

        // WRITE HEADER ONLY IF THE FILE DOES NOT EXIST YET
        if (!file.exists()) {
            try (BufferedWriter headerWriter = Files.newBufferedWriter(Paths.get(csvFilePath))) {
                headerWriter.write("NetworkName,Policies,ReachPerc,CompleteReachPerc,PortPerc,Algorithm,LambdaOptimization,LambdaPerformance,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");
            } catch (Exception e) {
                System.err.println("Error creating the CSV header: " + e.getMessage());
            }
        }

        int currentComb = 0;

        for (int pr : numberPRs) {
            for (double[] pair : percentPairs) {
                double reach    = pair[0];
                double complete = pair[1];

                for (double port : portSpecifics) {
                    for (String algo : algorithms) {
                        for (double lOpt : lambdaOptimizationVals) {
                            for (double lPerf : lambdaPerformanceVals) {
                                if (lOpt == 0 && lPerf == 0) continue;

                                currentComb++;
                                String networkName = String.format("%s_%dPR_r%.2f_c%.2f_p%.2f", prefix, pr, reach, complete, port);
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
                                        int runSeed = Math.abs(rand.nextInt());

                                        TestCaseGeneratorStanford generator = new TestCaseGeneratorStanford(
                                                networkName + "_run" + k, pr, runSeed, reach, complete, port
                                        );

                                        JAXBContext jc = JAXBContext.newInstance("it.polito.verefoo.jaxb");
                                        Unmarshaller u = jc.createUnmarshaller();
                                        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                                        Schema schema = sf.newSchema(new File("./xsd/nfvSchema.xsd"));
                                        u.setSchema(schema);

                                        NFV root = generator.getNfv();

                                        new VerefooSerializer(root, algo);

                                        sumGurobiTime += GurobiClusterer.getLastExecTime();
                                        sumObjValue += GurobiClusterer.getLastObjValue();
                                        sumActiveClusters += GurobiClusterer.getTotalActiveClusters();

                                        clusterNodes = GurobiClusterer.getLastClusterNodeCounts();
                                        clusterFlows = GurobiClusterer.getLastClusterFlowCounts();
                                        clusterAps   = GurobiClusterer.getLastClusterApCounts();

                                        validRuns++;

                                    } catch (Exception e) {
                                        System.err.println("Error in Stanford Run " + k + " (Algo: " + algo + "): " + e.getMessage());
                                    }
                                }

                                double avgGurobiTime     = validRuns > 0 ? sumGurobiTime     / validRuns : 0.0;
                                double avgObjValue       = validRuns > 0 ? sumObjValue       / validRuns : 0.0;
                                double avgActiveClusters = validRuns > 0 ? sumActiveClusters / validRuns : 0.0;

                                System.out.printf("Gurobi [%s] -> Time: %.3f s | Obj: %.2f | Clusters: %.1f\n",
                                        algo, avgGurobiTime, avgObjValue, avgActiveClusters);

                                String csvRow = String.format(java.util.Locale.US,
                                        "%s,%d,%.2f,%.2f,%.2f,%s,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                                        networkName, pr, reach, complete, port, algo,
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
        }
        System.out.println("\nStanford Policy Benchmark completed! Data successfully saved.");
    }
}