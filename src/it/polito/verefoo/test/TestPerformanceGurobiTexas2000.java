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
import it.polito.verefoo.extra.TestCaseGeneratorTexas2000;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.partitioning.GurobiClusterer;

public class TestPerformanceGurobiTexas2000 {

    static int seed = 999;
    static Random rand;

    // Texas2000 traffic configuration
    static boolean withPorts = false;
    static boolean withIsolation = true;
    static int isolationBound = 30;
    static final int runsPerConfig = 3;

    public static void main(String[] args) {
        System.out.println("Starting Gurobi Min-Cut Scalability Test on Texas2000 topology...");

        // ACTIVATING EARLY EXIT! We stop after partitioning.
        GurobiClusterer.setStopAfterGurobi(true);

        runScalabilityTest();
    }

    public static void runScalabilityTest() {
        rand = new Random(seed);

        // --- NETWORK GROWTH MATRIX (UCC, SS) ---
        int[][] networkSizes = {
                {1, 1}, {1, 2}, {2, 1}, {2, 2}, {2, 3}, {2, 4},
                {3, 1}, {3, 2}, {3, 3}, {3, 4}, {3, 5}, {3, 6},
                {3, 7}, {3, 8}, {3, 9}, {4, 1}, {4, 4}, {4, 8}
        };

        // --- ALGORITHMS AND LAMBDAS (Hyperparameters) ---
        String[] algorithms             = {"MF", "AP"};
        double[] lambdaOptimizationVals = {0.0, 0.3, 0.6, 1.0};
        double[] lambdaPerformanceVals  = {0.0, 0.3, 0.6, 1.0};

        int totalTestCases = networkSizes.length * algorithms.length
                * lambdaOptimizationVals.length * lambdaPerformanceVals.length;

        System.out.println("Total topologies to simulate: " + totalTestCases);

        String csvFilePath = "results_texas2000_gurobi_scalability.csv";

        try (BufferedWriter resFile = Files.newBufferedWriter(Paths.get(csvFilePath))) {

            resFile.write("NetworkName,UCC,SS,TotalNodes,TotalFirewalls,Algorithm,LambdaOptimization,LambdaPerformance,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");

            int currentComb = 0;

            for (int[] size : networkSizes) {
                int numberUCC = size[0];
                int numberSS  = size[1];

                int totalNodes     = 3 + 17 * numberUCC + 14 * numberSS;
                int totalFirewalls = 1 +  3 * numberUCC +  2 * numberSS;
                String networkName = "Texas_UCC" + numberUCC + "_SS" + numberSS;

                for (String algo : algorithms) {
                    for (double lOpt : lambdaOptimizationVals) {
                        for (double lPerf : lambdaPerformanceVals) {
                            if (lOpt == 0 && lPerf == 0) continue;

                            currentComb++;
                            System.out.println("\n--- [" + currentComb + "/" + totalTestCases + "] "
                                    + networkName + " (Nodes: " + totalNodes + ")"
                                    + " | Algo: " + algo
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

                                    TestCaseGeneratorTexas2000 generator = new TestCaseGeneratorTexas2000(
                                            runSeed, numberUCC, numberSS, withPorts, withIsolation, isolationBound
                                    );

                                    JAXBContext jc = JAXBContext.newInstance("it.polito.verefoo.jaxb");
                                    Unmarshaller u = jc.createUnmarshaller();
                                    SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                                    Schema schema = sf.newSchema(new File("./xsd/nfvSchema.xsd"));
                                    u.setSchema(schema);

                                    NFV root = generator.generateNFV(numberUCC, numberSS, withPorts, withIsolation, isolationBound);

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
                                    System.err.println("Error in Texas Run " + k + ": " + e.getMessage());
                                }
                            }

                            double avgGurobiTime     = validRuns > 0 ? sumGurobiTime     / validRuns : 0.0;
                            double avgObjValue       = validRuns > 0 ? sumObjValue       / validRuns : 0.0;
                            double avgActiveClusters = validRuns > 0 ? sumActiveClusters / validRuns : 0.0;

                            System.out.printf("Gurobi -> Time: %.3f s | Obj: %.2f | Clusters: %.1f\n",
                                    avgGurobiTime, avgObjValue, avgActiveClusters);

                            String csvRow = String.format(java.util.Locale.US,
                                    "%s,%d,%d,%d,%d,%s,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                                    networkName, numberUCC, numberSS, totalNodes, totalFirewalls, algo,
                                    lOpt, lPerf,
                                    avgGurobiTime, avgObjValue, avgActiveClusters,
                                    clusterNodes, clusterFlows, clusterAps);

                            resFile.write(csvRow + "\n");
                            resFile.flush();
                        }
                    }
                }
            }
            System.out.println("\nScalability Benchmark completed! Data saved to CSV.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}