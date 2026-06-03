package it.polito.verefoo.partitioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.polito.verefoo.jaxb.Node;
import com.gurobi.gurobi.*;

public class GurobiClusterer {

    // Object to carry the Edges
    public static class ClusterEdge {
        public Node u;
        public Node v;
        public double weight; // Sum of all penalties (lambda) on this link

        public ClusterEdge(Node u, Node v) {
            this.u = u;
            this.v = v;
            this.weight = 0.0;
        }
    }

    // For testing purposes only. To Be Removed
    private static Boolean stop_test = false;

    private static String LAST_CLUSTER_NODE_COUNTS = "";
    private static String LAST_CLUSTER_FLOW_COUNTS = "";
    private static String LAST_CLUSTER_AP_COUNTS = "";
    private static int totalAp = 0;
    // Public constants so VerefooProxy can use them to calculate the weight of the edges
    private static double LAMBDA_ISOLATION = 0.9;
    private static double LAMBDA_REACHABILITY = 0.6;
    private static double LAMBDA_3 = 0.5;  // Weight for network load balancing
    private static double LAMBDA_4 = 0.9; // Weight for Firewall balancing

    // Name of the analyzed network
    //private static String CURRENT_NETWORK_NAME = "Unknown_Network";
    //private String networkName;

    // Execution results
    private static double OBJECTIVE_RESULT = 0.0;
    private static double EXECUTION_TIME = 0.0;
    private static double ACTIVE_CLUSTERS = 0;

    private static final int N_MIN = 6;
    private static final int N_MAX = 40;

    private int fw_upper_bound = 0;
    private int flows_upper_bound = 0;

    // --- ILP Variables ---
    private int N, K_min, K_max;
    private List<Node> nodes;
    private int[] Fw;

    private List<ClusterEdge> edges;
    private Map<Node, Integer> nodeLoads; // How many flows physically traverse each node
    private Map<Node, Integer> nodeIndex = new HashMap<>();

    // References to Gurobi variables to extract the results later
    public GRBVar[][] X;
    public GRBVar[] A;

    // Receives Nodes, Weighted edges, and Node Loads
    public GurobiClusterer(List<Node> originalNodes, List<ClusterEdge> originalEdges, Map<Node, Integer> nodeLoads) {

        System.out.println(">>> Avvio Graph Pruning (Pre-Processing)...");

        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.nodeLoads = nodeLoads;

        int prunedNodesCount = 0;
        int prunedEdgesCount = 0;

        // We use a Set for lightning-fast checking of the subsequent edges
        java.util.Set<Node> validNodesSet = new java.util.HashSet<>();

        // Filter the nodes
        for (Node n : originalNodes) {
            //boolean isAllocationPlace = (n.getFunctionalType() == null);
            int load = nodeLoads.getOrDefault(n, 0);

            if (load > 0) {
                this.nodes.add(n);
                validNodesSet.add(n); // We also add it to the Set
            } else {
                prunedNodesCount++;
            }
        }

        // Filter the edges in O(1) time per edge
        for (ClusterEdge e : originalEdges) {
            if (validNodesSet.contains(e.u) && validNodesSet.contains(e.v)) {
                this.edges.add(e);
            } else {
                prunedEdgesCount++;
            }
        }

        // Update N with the new number of "useful" nodes
        this.N = this.nodes.size();

        // Proceed with the normal logic...
        this.K_min = (int) Math.ceil((double) N / N_MAX);
        this.K_max = N / N_MIN;

        // Node index creation
        for (int i = 0; i < N; i++) {
            nodeIndex.put(this.nodes.get(i), i);
        }

        // Allocation Places extraction
        this.Fw = new int[N];
        totalAp = 0;
        for (int i = 0; i < N; i++) {
            Node n = this.nodes.get(i);
            if (n.getFunctionalType() == null) {
                totalAp += 1;
                Fw[i] = 1;
            } else {
                Fw[i] = 0;
            }
        }

        fw_upper_bound = Math.min(totalAp, N_MAX);

        for (int load : nodeLoads.values()) {
            flows_upper_bound += load;
        }

        System.out.println("Graph Pruning completed:");
        System.out.println(" - Deleted nodes  : " + prunedNodesCount + " (Remaining: " + N + ")");
        System.out.println(" - Deleted edges : " + prunedEdgesCount + " (Remaining: " + this.edges.size() + ")");
    }

    public void solve_ILP() throws GRBException {
        // Initialization of the Gurobi environment (OutputFlag = 0 to silence verbose logs)
        GRBEnv env = new GRBEnv(true);
        env.set(GRB.IntParam.OutputFlag, 0);
        env.start();
        //env.set(GRB.DoubleParam.MIPGap, 0.05); // Stops when it reaches 95% of the absolute optimum
        // Optional: set an absolute time limit (in this case 12 minutes)
        // This is a soft limit checked at specific interval. When the limit expires, gurobi don't terminate immediately, but it first completes its current node
        // or algorithmic step to save the best feasible solution it has found so far.
        env.set(GRB.DoubleParam.TimeLimit, 60.0);
        GRBModel model = null;

        // how to optimize even more
        // 2. Limit the Presolve phase.
        // Presolve can sometimes take longer than the actual optimization on dense graphs.
        // '1' means standard presolve (default is usually -1/automatic which can be too aggressive)
        env.set(GRB.IntParam.Presolve, 1);

        // 3. Change the Method used for the Root Node.
        // Default (-1) lets Gurobi choose, often picking 'Concurrent' (running all algorithms at once).
        // Setting it to '2' forces the Barrier method, which is generally faster and more predictable for large, sparse network graphs.
        env.set(GRB.IntParam.Method, 2);

        try {
            model = new GRBModel(env);
            int numEdges = edges.size();

            // Decision Variables

            // X[i][k] = 1 if node i belongs to cluster k
            X = new GRBVar[N][K_max];
            for (int i = 0; i < N; i++){
                for (int k = 0; k < K_max; k++) {
                    X[i][k] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "X_" + i + "_" + k);
                }
            }

            // A[k] = 1 if cluster k is active (contains nodes)
            A = new GRBVar[K_max];
            for (int k = 0; k < K_max; k++){
                A[k] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "A_" + k);
            }

            // Z[e] = 1 if edge e is "cut" (nodes u and v end up in different clusters)
            GRBVar Z[] = new GRBVar[numEdges];
            for(int e = 0; e < numEdges; e++) {
                // Z will become binary anyway, since X is already binary. It makes no sense to impose an extra constraint
                Z[e] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "Z_" + e);

                // Retrieve the integer indices of nodes u and v of this edge
                int u = nodeIndex.get(edges.get(e).u);
                int v = nodeIndex.get(edges.get(e).v);

                // Min-Cut Constraints: If U and V are in different clusters k, Z[e] MUST become 1
                for(int k = 0; k < K_max; k++) {

                    // Create the expression: X[u][k] - X[v][k]
                    GRBLinExpr diff1 = new GRBLinExpr();
                    diff1.addTerm(1.0, X[u][k]);
                    diff1.addTerm(-1.0, X[v][k]);
                    model.addConstr(Z[e], GRB.GREATER_EQUAL, diff1, "Cut1_" + e + "_" + k);

                    // Create the expression: X[v][k] - X[u][k]
                    GRBLinExpr diff2 = new GRBLinExpr();
                    diff2.addTerm(1.0, X[v][k]);
                    diff2.addTerm(-1.0, X[u][k]);
                    model.addConstr(Z[e], GRB.GREATER_EQUAL, diff2, "Cut2_" + e + "_" + k);

                }
            }

            // Variables for Min-Max balancing
            GRBVar maxFlows = model.addVar(0.0, flows_upper_bound, 0.0, GRB.CONTINUOUS, "Max_Flows");
            GRBVar maxFw = model.addVar(0.0, fw_upper_bound, 0.0, GRB.INTEGER, "Max_Fw");

            // Geometric and Capability Constraints

            // Each node must belong to exactly one active cluster
            for (int i = 0; i < N; i++){
                GRBLinExpr sumK = new GRBLinExpr();
                for (int k = 0; k < K_max; k++) {
                    sumK.addTerm(1.0, X[i][k]);
                    // Link the use of X to the activation of cluster A
                    model.addConstr(X[i][k], GRB.LESS_EQUAL, A[k], "Link_X_A_" + i + "_" + k);
                }
                model.addConstr(sumK, GRB.EQUAL, 1.0, "One_Cluster_" + i);
            }

            // Cluster size limits (N_MIN and N_MAX)
            for (int k = 0; k < K_max; k++) {
                GRBLinExpr sizeK = new GRBLinExpr();
                for (int i = 0; i < N; i++) {
                    sizeK.addTerm(1.0, X[i][k]);
                }

                GRBLinExpr minSize = new GRBLinExpr();
                minSize.addTerm((double) N_MIN, A[k]);
                model.addConstr(sizeK, GRB.GREATER_EQUAL, minSize, "Min_Size_" + k);

                GRBLinExpr maxSize = new GRBLinExpr();
                maxSize.addTerm((double) N_MAX, A[k]);
                model.addConstr(sizeK, GRB.LESS_EQUAL, maxSize, "Max_Size_" + k);
            }

            // Limits on the total number of active clusters
            GRBLinExpr activeK = new GRBLinExpr();
            for (int k = 0; k < K_max; k++) {
                activeK.addTerm(1.0, A[k]);
            }
            model.addConstr(activeK, GRB.GREATER_EQUAL, K_min, "Min_Active_Clusters");

            // Balancing Constraints

            // Network Load Balancing (Node Loads)
            for (int k = 0; k < K_max; k++) {
                GRBLinExpr loadK = new GRBLinExpr();
                for (int i = 0; i < N; i++) {
                    Node n = nodes.get(i);
                    int load = nodeLoads.getOrDefault(n, 0);
                    loadK.addTerm(load, X[i][k]);
                }
                model.addConstr(maxFlows, GRB.GREATER_EQUAL, loadK, "Max_Flows_Bound_" + k);
            }

            // Firewall Balancing
            for (int k = 0; k < K_max; k++) {
                GRBLinExpr fwK = new GRBLinExpr();
                for (int i = 0; i < N; i++) {
                    if (Fw[i] == 1) {
                        fwK.addTerm(1.0, X[i][k]);
                    }
                }
                model.addConstr(maxFw, GRB.GREATER_EQUAL, fwK, "Max_Fw_Bound_" + k);
            }

            // Objective Function

            GRBLinExpr objective = new GRBLinExpr();

            // Add the edge cuts: if Z[e] is 1 (cut), we pay the 'weight' of the edge
            for (int e = 0; e < numEdges; e++) {
                double weight = edges.get(e).weight;
                objective.addTerm(weight, Z[e]);
            }

            // Push down the maximum peak of loads and firewalls to balance them
            objective.addTerm(LAMBDA_3, maxFlows);
            objective.addTerm(LAMBDA_4, maxFw);

            model.setObjective(objective, GRB.MINIMIZE);

            // Solver Execution

            model.write("my_min_cut_model.lp"); // Useful for debugging
            model.optimize();

            // Output and results check
            if (model.get(GRB.IntAttr.Status) == GRB.INFEASIBLE) {
                System.err.println("The ILP model is Infeasible. Check the N_MIN and N_MAX limits.");
            } else {
                System.out.println("\n--- ILP CLUSTERING RESULT (MIN-CUT) ---");
                int total_active_clusters = 0;
                for (int k = 0; k < K_max; k++) {
                    if (Math.round(A[k].get(GRB.DoubleAttr.X)) == 1) {
                        total_active_clusters++;
                        System.out.println("\n--- CLUSTER " + k + " ---");

                        List<String> clusterNodes = new ArrayList<>();
                        int fwCount = 0;
                        int totalLoad = 0;

                        for (int i = 0; i < N; i++) {
                            if (Math.round(X[i][k].get(GRB.DoubleAttr.X)) == 1) {
                                Node n = nodes.get(i);
                                clusterNodes.add(n.getName());
                                if (Fw[i] == 1) fwCount++;
                                totalLoad += nodeLoads.getOrDefault(n, 0);
                            }
                        }

                        System.out.println("Nodes (" + clusterNodes.size() + ")    : " + clusterNodes);
                        System.out.println("Allocable Firewalls : " + fwCount);
                        System.out.println("Flow Load: " + totalLoad);
                    }
                }
                OBJECTIVE_RESULT = model.get(GRB.DoubleAttr.ObjVal);
                EXECUTION_TIME = model.get(GRB.DoubleAttr.Runtime);
                ACTIVE_CLUSTERS = total_active_clusters;

                System.out.println("---------------------------------------");
                System.out.println("Total Active Clusters: " + ACTIVE_CLUSTERS);
                // This is the value of the objective function
                System.out.println("Objective Value      : " + OBJECTIVE_RESULT);
                System.out.println("Execution Time       : " + String.format("%.3f", EXECUTION_TIME) + " s");

                List<Integer> nodeCounts = new java.util.ArrayList<>();
                List<Integer> flowCounts = new java.util.ArrayList<>();
                List<Integer> apCounts = new java.util.ArrayList<>();

                for (int k = 0; k < K_max; k++) {
                    // If cluster k is active (A[k] == 1)
                    if (Math.round(A[k].get(GRB.DoubleAttr.X)) == 1) {
                        int nodesInCluster = 0;
                        int apsInCluster = 0;
                        int loadInCluster = 0;

                        for (int i = 0; i < N; i++) {
                            // If node i is assigned to cluster k
                            if (Math.round(X[i][k].get(GRB.DoubleAttr.X)) == 1) {
                                nodesInCluster++;

                                // Count Allocation Places
                                if (Fw[i] == 1) {
                                    apsInCluster++;
                                }

                                // Sum the flow load for this node
                                Node n = nodes.get(i);
                                loadInCluster += nodeLoads.getOrDefault(n, 0);
                            }
                        }

                        nodeCounts.add(nodesInCluster);
                        apCounts.add(apsInCluster);
                        flowCounts.add(loadInCluster); // Using aggregate load as our flow proxy
                    }
                }

                // Convert lists to semicolon-separated strings
                LAST_CLUSTER_NODE_COUNTS = nodeCounts.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(";"));
                LAST_CLUSTER_FLOW_COUNTS = flowCounts.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(";"));
                LAST_CLUSTER_AP_COUNTS = apCounts.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(";"));
            }
        } finally {
            if (model != null)
                model.dispose();
            env.dispose();
        }
    }

    // Statics Getters and Setters
    // Setters per le Lambda (utilizzati dal Test Runner)
    public static void setLambdaIsolation(double lambda) { LAMBDA_ISOLATION = lambda; }
    public static void setLambdaReachability(double lambda) { LAMBDA_REACHABILITY = lambda; }
    public static void setLambda3(double lambda) { LAMBDA_3 = lambda; }
    public static void setLambda4(double lambda) { LAMBDA_4 = lambda; }

    // Setter for testing purpose
    public static void setStopAfterGurobi(Boolean stop_value) { stop_test = stop_value; }

    // Getters for Lambdas
    public static double getLambdaIsolation() { return LAMBDA_ISOLATION; }
    public static double getLambdaReachability() { return LAMBDA_REACHABILITY; }
    public static double getLambda3() { return LAMBDA_3; }
    public static double getLambda4() { return LAMBDA_4; }

    // Getters for Results
    public static double getLastObjValue() { return OBJECTIVE_RESULT; }
    public static double getLastExecTime() { return EXECUTION_TIME; }
    public static double getTotalActiveClusters() {return ACTIVE_CLUSTERS;}

    public static String getLastClusterNodeCounts() { return LAST_CLUSTER_NODE_COUNTS; }
    public static String getLastClusterFlowCounts() { return LAST_CLUSTER_FLOW_COUNTS; }
    public static String getLastClusterApCounts() { return LAST_CLUSTER_AP_COUNTS; }

    // Getter for testing purpose
    public static Boolean getStopAfterGurobi() { return stop_test; }
}