package it.polito.verefoo.test;

import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

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
        // Varying the number of policies to test scalability on a fixed topology
        int[] policyNumbers = {10};

        // Array for reachability percentages (e.g., 0.5 means 50% Reachability, 50% Isolation)
        double[] reachabilityPercs = {0.5};

        // --- ALGORITHMS AND LAMBDAS (Hyperparameters) ---
        String[] algorithms      = {"MF", "AP"};

        // Golden standard values from previous tuning (Change them if needed)
        double[] lambdaIsoVals   = {1.0};
        double[] lambdaReachVals = {0.2};
        double[] lambda3Vals     = {0.5}; // Load balancing weight
        double[] lambda4Vals     = {0.1}; // Firewall balancing weight

        int totalCombinations = policyNumbers.length * reachabilityPercs.length * algorithms.length * lambdaIsoVals.length * lambdaReachVals.length * lambda3Vals.length * lambda4Vals.length;

        System.out.println("Total combinations to explore: " + totalCombinations);

        String csvFilePath = "results_cesnet_gurobi_policies.csv";
        File file = new File(csvFilePath);

        // WRITE HEADER ONLY IF THE FILE DOES NOT EXIST YET
        if (!file.exists()) {
            try (BufferedWriter headerWriter = Files.newBufferedWriter(Paths.get(csvFilePath))) {
                headerWriter.write("NetworkName,Policies,ReachPerc,Algorithm,LambdaIso,LambdaReach,Lambda3,Lambda4,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");
            } catch (Exception e) {
                System.err.println("Error creating the CSV header: " + e.getMessage());
            }
        }

        int currentComb = 0;

        // START OF NESTED LOOPS
        for (int policyNum : policyNumbers) {
            for (double reachPerc : reachabilityPercs) {
                for (String algo : algorithms) {
                    for (double lIso : lambdaIsoVals) {
                        for (double lReach : lambdaReachVals) {
                            for (double l3 : lambda3Vals) {
                                for (double l4 : lambda4Vals) {

                                    currentComb++;
                                    // Generate a meaningful network name based on the current parameters
                                    String networkName = String.format("Cesnet_%dPol_r%.2f", policyNum, reachPerc);
                                    System.out.println("\n--- [" + currentComb + "/" + totalCombinations + "] " + networkName + " | Algo: " + algo + " ---");

                                    // Set current Lambdas for Gurobi
                                    GurobiClusterer.setLambdaIsolation(lIso);
                                    GurobiClusterer.setLambdaReachability(lReach);
                                    GurobiClusterer.setLambda3(l3);
                                    GurobiClusterer.setLambda4(l4);

                                    // Accumulators for averages
                                    double sumGurobiTime = 0.0;
                                    double sumObjValue = 0.0;
                                    double sumActiveClusters = 0.0;
                                    int validRuns = 0;

                                    // Temporary strings to hold cluster representations
                                    String clusterNodes = "";
                                    String clusterFlows = "";
                                    String clusterAps = "";

                                    // Robustness loop (averaging results over multiple runs)
                                    for (int k = 0; k < runsPerConfig; k++) {
                                        try {
                                            // Initialize the Cesnet generator
                                            // Note: Cesnet generator randomizes policies internally during initialization
                                            TestCaseGeneratorCesnet generator = new TestCaseGeneratorCesnet(
                                                    fileName, isolationBidirectional, policyNum, reachPerc, usePorts
                                            );

                                            JAXBContext jc = JAXBContext.newInstance("it.polito.verefoo.jaxb");
                                            Unmarshaller u = jc.createUnmarshaller();
                                            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                                            Schema schema = sf.newSchema(new File("./xsd/nfvSchema.xsd"));
                                            u.setSchema(schema);

                                            NFV root = generator.getNFV();

                                            // RUN VEREFOO (It will stop immediately after Gurobi due to the Early Exit flag)
                                            new VerefooSerializer(root, algo);

                                            // Extract metrics directly from Gurobi
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

                                    // Calculate averages (avoiding division by zero)
                                    double avgGurobiTime = validRuns > 0 ? sumGurobiTime / validRuns : 0.0;
                                    double avgObjValue = validRuns > 0 ? sumObjValue / validRuns : 0.0;
                                    double avgActiveClusters = validRuns > 0 ? sumActiveClusters / validRuns : 0.0;

                                    System.out.printf("Gurobi [%s] -> Time: %.3f s | Obj: %.2f | Clusters: %.1f\n",
                                            algo, avgGurobiTime, avgObjValue, avgActiveClusters);

                                    // Write row in Append mode
                                    String csvRow = String.format(java.util.Locale.US, "%s,%d,%.2f,%s,%.1f,%.1f,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                                            networkName, policyNum, reachPerc, algo,
                                            lIso, lReach, l3, l4,
                                            avgGurobiTime, avgObjValue, avgActiveClusters,
                                            clusterNodes, clusterFlows, clusterAps);

                                    try (BufferedWriter rowWriter = java.nio.file.Files.newBufferedWriter(
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
        }
        System.out.println("\nCESNET Policy Benchmark completed! Data successfully saved.");
    }
}