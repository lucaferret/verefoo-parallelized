package it.polito.verefoo.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.TestCaseGeneratorCesnet;
import it.polito.verefoo.extra.TestCaseGeneratorStanford;
import it.polito.verefoo.extra.TestCaseGeneratorTexas2000;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.partitioning.GurobiClusterer;

public class TestBenchmarkOrchestrator {

    // --- TELEGRAM CONFIGURATION ---
    private static final String TELEGRAM_TOKEN = "YOUR_BOT_TOKEN_HERE";
    private static final String TELEGRAM_CHAT_ID = "YOUR_CHAT_ID_HERE";

    // --- BENCHMARK CONFIGURATION ---
    private static final int RUNS_PER_CONFIG = 10; // 20 runs for robust average data
    static int baseSeed = 999;
    static Random rand;

    // --- HYPERPARAMETER ARRAYS (LAMBDAS) ---
    // You can freely add or remove values here to test multiple combinations
    static double[] lambdaIsoVals   = {1.0, 0.8};
    static double[] lambdaReachVals = {0.2, 0.5};
    static double[] lambda3Vals     = {0.5};       // Load balancing weight
    static double[] lambda4Vals     = {0.1, 0.5};  // Firewall balancing weight

    public static void main(String[] args) {
        sendTelegramMessage("🏁 *Gurobi Benchmark Marathon Started!* \nAll 3 suites will run continuously. Hyperparameter grid search enabled.");

        // Force early exit to benchmark only the partitioning phase
        GurobiClusterer.setStopAfterGurobi(true);
        rand = new Random(baseSeed);

        // ==========================================
        // 1. RUN STANFORD BENCHMARK
        // ==========================================

        try {
            runStanfordSuite();
            sendTelegramFile("results_stanford_gurobi_policies.csv");
        } catch (Exception e) {
            sendTelegramMessage("*Fatal Error in Stanford Suite:* \n" + e.getMessage());
            e.printStackTrace();
        }


        // ==========================================
        // 2. RUN TEXAS2000 BENCHMARK
        // ==========================================
        try {
            runTexasSuite();
            sendTelegramFile("results_texas2000_gurobi_scalability.csv");
        } catch (Exception e) {
            sendTelegramMessage("*Fatal Error in Texas2000 Suite:* \n" + e.getMessage());
            e.printStackTrace();
        }

        // ==========================================
        // 3. RUN CESNET BENCHMARK
        // ==========================================
        try {
            runCesnetSuite();
            sendTelegramFile("results_cesnet_gurobi_policies.csv");
        } catch (Exception e) {
            sendTelegramMessage("*Fatal Error in Cesnet Suite:* \n" + e.getMessage());
            e.printStackTrace();
        }

        sendTelegramMessage("*Gurobi Benchmark Marathon Finished!* \nCheck the logs above for any suite-specific errors.");
    }
    // =========================================================================
    // 1. STANFORD SUITE
    // =========================================================================
    private static void runStanfordSuite() {
        sendTelegramMessage("*[1/3] Starting Stanford Suite with Lambda Grid Search...*");
        String csvPath = "results_stanford_gurobi_policies.csv";
        prepareCsvHeader(csvPath, "NetworkName,Policies,ReachPerc,CompleteReachPerc,PortPerc,Algorithm,LambdaIso,LambdaReach,Lambda3,Lambda4,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");

        int[] numberPRs = {10, 20, 30, 40, 50, 60, 80};
        String[] algorithms = {"MF", "AP"};

        for (int pr : numberPRs) {
            for (String algo : algorithms) {
                for (double lIso : lambdaIsoVals) {
                    for (double lReach : lambdaReachVals) {
                        for (double l3 : lambda3Vals) {
                            for (double l4 : lambda4Vals) {
                                String networkName = "Stanford_" + pr + "PR";
                                executeBenchmarkStep(csvPath, networkName, algo, pr, 0.5, 0.3, 0.1, 1, lIso, lReach, l3, l4);
                            }
                        }
                    }
                }
            }
        }
        sendTelegramMessage("*Stanford Suite Completed!* File saved.");
    }

    // =========================================================================
    // 2. TEXAS2000 SUITE (Scalability Wall Test)
    // =========================================================================
    private static void runTexasSuite() {
        sendTelegramMessage("*[2/3] Starting Texas2000 Suite with Lambda Grid Search...*");
        String csvPath = "results_texas2000_gurobi_scalability.csv";
        prepareCsvHeader(csvPath, "NetworkName,UCC,SS,TotalNodes,TotalFirewalls,Algorithm,LambdaIso,LambdaReach,Lambda3,Lambda4,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");

        int[][] networkSizes = {
                {1, 1}, {1, 2}, {2, 1}, {2, 2}, {2, 3}, {2, 4},
                {3, 1}, {3, 2}, {3, 3}, {3, 4}, {3, 5}, {3, 6},
                {3, 7}, {3, 8}, {3, 9}, {4, 1}, {4, 4}, {4, 8}
        };
        String[] algorithms = {"MF", "AP"};

        for (int[] size : networkSizes) {
            int ucc = size[0];
            int ss = size[1];
            String networkName = "Texas_UCC" + ucc + "_SS" + ss;
            for (String algo : algorithms) {
                for (double lIso : lambdaIsoVals) {
                    for (double lReach : lambdaReachVals) {
                        for (double l3 : lambda3Vals) {
                            for (double l4 : lambda4Vals) {
                                executeBenchmarkStep(csvPath, networkName, algo, ucc, ss, 0, 0, 2, lIso, lReach, l3, l4);
                            }
                        }
                    }
                }
            }
        }
        sendTelegramMessage("*Texas2000 Suite Completed!* File saved.");
    }

    // =========================================================================
    // 3. CESNET SUITE (Real Network Test)
    // =========================================================================
    private static void runCesnetSuite() {
        sendTelegramMessage("*[3/3] Starting CESNET Suite with Lambda Grid Search...*");
        String csvPath = "results_cesnet_gurobi_policies.csv";
        prepareCsvHeader(csvPath, "NetworkName,Policies,ReachPerc,Algorithm,LambdaIso,LambdaReach,Lambda3,Lambda4,AvgGurobiTime_s,AvgObjValue,AvgActiveClusters,ClusterNodeCounts,ClusterFlowCounts,ClusterAPCounts\n");

        int[] policyNumbers = {10, 20, 30, 40, 45, 50};
        String[] algorithms = {"MF", "AP"};

        for (int policyNum : policyNumbers) {
            for (String algo : algorithms) {
                for (double lIso : lambdaIsoVals) {
                    for (double lReach : lambdaReachVals) {
                        for (double l3 : lambda3Vals) {
                            for (double l4 : lambda4Vals) {
                                String networkName = "Cesnet_" + policyNum + "Pol";
                                executeBenchmarkStep(csvPath, networkName, algo, policyNum, 0.5, 0, 0, 3, lIso, lReach, l3, l4);
                            }
                        }
                    }
                }
            }
        }
        sendTelegramMessage("*CESNET Suite Completed!* File saved.");
    }

    // =========================================================================
    // CORE EXECUTION STEP ENGINE
    // =========================================================================
    private static void executeBenchmarkStep(String csvPath, String networkName, String algo, double p1, double p2, double p3, double p4, int suiteId, double lIso, double lReach, double l3, double l4) {

        // Dynamic configuration of hyperparameters for this specific grid point
        GurobiClusterer.setLambdaIsolation(lIso);
        GurobiClusterer.setLambdaReachability(lReach);
        GurobiClusterer.setLambda3(l3);
        GurobiClusterer.setLambda4(l4);

        double sumGurobiTime = 0.0; double sumObjValue = 0.0; double sumActiveClusters = 0.0;
        int validRuns = 0;
        String clusterNodes = ""; String clusterFlows = ""; String clusterAps = "";

        System.out.printf("Executing: %s [%s] | Lambdas: Iso=%.1f, Reach=%.1f, L3=%.1f, L4=%.1f\n",
                networkName, algo, lIso, lReach, l3, l4);

        for (int k = 0; k < RUNS_PER_CONFIG; k++) {
            try {
                int runSeed = Math.abs(rand.nextInt());
                NFV root = null;

                if (suiteId == 1) { // Stanford
                    TestCaseGeneratorStanford gen = new TestCaseGeneratorStanford(networkName + "_r" + k, (int)p1, runSeed, p2, p3, p4);
                    root = gen.getNfv();
                } else if (suiteId == 2) { // Texas
                    TestCaseGeneratorTexas2000 gen = new TestCaseGeneratorTexas2000(runSeed, (int)p1, (int)p2, false, true, 30);
                    root = gen.generateNFV((int)p1, (int)p2, false, true, 30);
                } else if (suiteId == 3) { // Cesnet
                    TestCaseGeneratorCesnet gen = new TestCaseGeneratorCesnet("testfile/CESNET/cesnet1", true, (int)p1, p2, true);
                    root = gen.getNFV();
                }

                JAXBContext jc = JAXBContext.newInstance("it.polito.verefoo.jaxb");
                Unmarshaller u = jc.createUnmarshaller();
                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = sf.newSchema(new File("./xsd/nfvSchema.xsd"));
                u.setSchema(schema);

                new VerefooSerializer(root, algo);

                sumGurobiTime += GurobiClusterer.getLastExecTime();
                sumObjValue += GurobiClusterer.getLastObjValue();
                sumActiveClusters += GurobiClusterer.getTotalActiveClusters();

                clusterNodes = GurobiClusterer.getLastClusterNodeCounts();
                clusterFlows = GurobiClusterer.getLastClusterFlowCounts();
                clusterAps   = GurobiClusterer.getLastClusterApCounts();
                validRuns++;

            } catch (Exception e) {
                System.err.println("⚠ Run error or Gurobi Timeout in " + networkName + ": " + e.getMessage());
            }
        }

        double avgTime = validRuns > 0 ? sumGurobiTime / validRuns : 0.0;
        double avgObj = validRuns > 0 ? sumObjValue / validRuns : 0.0;
        double avgClusters = validRuns > 0 ? sumActiveClusters / validRuns : 0.0;

        String csvRow;
        if (suiteId == 1) {
            csvRow = String.format(java.util.Locale.US, "%s,%d,%.2f,%.2f,%.2f,%s,%.1f,%.1f,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                    networkName, (int)p1, p2, p3, p4, algo, lIso, lReach, l3, l4, avgTime, avgObj, avgClusters, clusterNodes, clusterFlows, clusterAps);
        } else if (suiteId == 2) {
            int nodes = 3 + 17 * (int)p1 + 14 * (int)p2;
            int fws = 1 + 3 * (int)p1 + 2 * (int)p2;
            csvRow = String.format(java.util.Locale.US, "%s,%d,%d,%d,%d,%s,%.1f,%.1f,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                    networkName, (int)p1, (int)p2, nodes, fws, algo, lIso, lReach, l3, l4, avgTime, avgObj, avgClusters, clusterNodes, clusterFlows, clusterAps);
        } else {
            csvRow = String.format(java.util.Locale.US, "%s,%d,%.2f,%s,%.1f,%.1f,%.1f,%.1f,%.3f,%.2f,%.1f,\"%s\",\"%s\",\"%s\"",
                    networkName, (int)p1, p2, algo, lIso, lReach, l3, l4, avgTime, avgObj, avgClusters, clusterNodes, clusterFlows, clusterAps);
        }

        try (BufferedWriter rowWriter = Files.newBufferedWriter(Paths.get(csvPath), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            rowWriter.write(csvRow + "\n");
        } catch (Exception e) {
            System.err.println("Fatal CSV writing error: " + e.getMessage());
        }

        // Send updates for meaningful thresholds to monitor progress on Telegram
        if (avgTime > 30.0 || networkName.contains("60PR") || networkName.contains("300Pol") || networkName.contains("UCC3")) {
            sendTelegramMessage(String.format("*Grid Search Step Update* \nNet: `%s` [%s]\nLambdas: `[%.1f, %.1f, %.1f, %.1f]`\nAvg Time: `%.2fs` \nObj: `%.2f`",
                    networkName, algo, lIso, lReach, l3, l4, avgTime, avgObj));
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

    // =========================================================================
    // TELEGRAM SENDER HTTP GATEWAY
    // =========================================================================
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
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        } catch (Exception e) {
            System.err.println("Connection failure with Telegram API Gateway: " + e.getMessage());
        }
    }

    // =========================================================================
    // TELEGRAM FILE UPLOADER (MULTIPART FORM-DATA GATEWAY)
    // =========================================================================
    private static void sendTelegramFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File not found for Telegram upload: " + filePath);
            return;
        }

        String boundary = "===Boundary===" + System.currentTimeMillis();
        String lineFeed = "\r\n";

        try {
            URL url = new URL("https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendDocument");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            java.io.OutputStream outputStream = conn.getOutputStream();
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"), true);

            // 1. Append the chat_id field part
            writer.append("--" + boundary).append(lineFeed);
            writer.append("Content-Disposition: form-data; name=\"chat_id\"").append(lineFeed);
            writer.append(lineFeed);
            writer.append(TELEGRAM_CHAT_ID).append(lineFeed);
            writer.flush();

            // 2. Append the document file part header
            writer.append("--" + boundary).append(lineFeed);
            writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"" + file.getName() + "\"").append(lineFeed);
            writer.append("Content-Type: text/csv").append(lineFeed);
            writer.append(lineFeed);
            writer.flush();

            // 3. Stream the raw file bytes directly to the connection output
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            outputStream.write(fileBytes);
            outputStream.flush();

            // 4. Close the multipart form data boundary
            writer.append(lineFeed);
            writer.append("--" + boundary + "--").append(lineFeed);
            writer.flush();
            writer.close();

            // Process response code to verify delivery
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("File sent successfully via Telegram: " + file.getName());
            } else {
                System.err.println("Failed to send file via Telegram. HTTP code: " + responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("Error uploading file to Telegram: " + e.getMessage());
        }
    }
}