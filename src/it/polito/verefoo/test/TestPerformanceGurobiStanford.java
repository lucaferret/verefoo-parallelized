package it.polito.verefoo.test;

import static org.junit.Assert.fail;

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
    static int seed = 999;
    static Random rand;

    static final int numberPR = 50;
    static final double reach = 0.5;
    static final double complete = 0.3;
    static final double port = 0.1;
    static final int runsPerConfig = 3;

    public static void main(String[] args) {
        System.out.println("Starting Gurobi Min-Cut Grid Search (Partitioning Only)...");
        //GurobiClusterer.setStopAfterGurobi(true);
        runGridSearch();
    }

    public static void runGridSearch() {
        rand = new Random(seed);

        String[] algorithms      = {"MF", "AP"};
        double[] lambdaIsoVals   = {0.5, 1.0};
        double[] lambdaReachVals = {0.2, 0.5};
        double[] lambda3Vals     = {0.1, 0.5};
        double[] lambda4Vals     = {0.1, 0.5};

        int totalCombinations = algorithms.length * lambdaIsoVals.length * lambdaReachVals.length * lambda3Vals.length * lambda4Vals.length;
        System.out.println("Total combinations to explore: " + totalCombinations);

        String csvFilePath = "results_stanford_gurobi_gridsearch.csv";

        try (BufferedWriter resFile = Files.newBufferedWriter(Paths.get(csvFilePath))) {

            // NEW CSV HEADER: Added detailed cluster metrics columns
            resFile.write("NetworkName,Algorithm,LambdaIso,LambdaReach,Lambda3,Lambda4,AvgGurobiTime_s,AvgObjValue,avgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");

            int currentComb = 0;

            for (String algo : algorithms) {
                for (double lIso : lambdaIsoVals) {
                    for (double lReach : lambdaReachVals) {
                        for (double l3 : lambda3Vals) {
                            for (double l4 : lambda4Vals) {

                                currentComb++;
                                String networkName = prefix + "_" + numberPR + "PR";
                                System.out.println("\n--- [" + currentComb + "/" + totalCombinations + "] Algo:" + algo + " Iso:" + lIso + " Reach:" + lReach + " L3:" + l3 + " L4:" + l4 + " ---");

                                GurobiClusterer.setLambdaIsolation(lIso);
                                GurobiClusterer.setLambdaReachability(lReach);
                                GurobiClusterer.setLambda3(l3);
                                GurobiClusterer.setLambda4(l4);
                                //GurobiClusterer.setCurrentNetworkName(networkName);

                                double sumGurobiTime = 0.0;
                                double sumObjValue = 0.0;
                                int sumActiveClusters = 0;
                                int validRuns = 0;

                                // Temporary variables to save the strings from the last valid configuration
                                String clusterNodes = "";
                                String clusterFlows = "";
                                String clusterAps = "";

                                for (int k = 0; k < runsPerConfig; k++) {
                                    try {
                                        TestCaseGeneratorStanford generator = new TestCaseGeneratorStanford(
                                                networkName + "_run" + k, numberPR, Math.abs(rand.nextInt()), reach, complete, port
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

                                        // Retrieve the cluster details using the new getters
                                        clusterNodes = GurobiClusterer.getLastClusterNodeCounts();
                                        clusterFlows = GurobiClusterer.getLastClusterFlowCounts();
                                        clusterAps   = GurobiClusterer.getLastClusterApCounts();

                                        validRuns++;

                                    } catch (Exception e) {
                                        System.err.println("Error or Gurobi Timeout during run: " + e.getMessage());
                                    }
                                }

                                double avgGurobiTime = validRuns > 0 ? sumGurobiTime / validRuns : 0.0;
                                double avgObjValue = validRuns > 0 ? sumObjValue / validRuns : 0.0;
                                double avgActiveClusters = validRuns > 0 ? sumActiveClusters / validRuns : 0.0;

                                System.out.printf("Gurobi -> Time: %.3f s | Obj: %.2f | Nodes per Cluster: [%s]\n",
                                        avgGurobiTime, avgObjValue, clusterNodes);

                                // NEW CSV ROW FORMAT: Added the lists wrapped in quotes to prevent breaking CSV cells
                                String csvRow = String.format(java.util.Locale.US, "%s,%s,%.1f,%.1f,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                                        networkName, algo, lIso, lReach, l3, l4, avgGurobiTime, avgObjValue, avgActiveClusters, clusterNodes, clusterFlows, clusterAps);

                                resFile.write(csvRow + "\n");
                                resFile.flush();
                            }
                        }
                    }
                }
            }
            System.out.println("\nBenchmark completed! Structured data successfully saved.");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }
}