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

    // Public constants so VerefooProxy can use them to calculate the weight of the edges
    public static final double LAMBDA_ISOLATION = 0.9;
    public static final double LAMBDA_REACHABILITY = 0.6;
    private static final double LAMBDA_3 = 0.5;  // Weight for network load balancing
    private static final double LAMBDA_4 = 0.9; // Weight for Firewall balancing

    private static final int N_MIN = 6;
    private static final int N_MAX = 25;

    private int fw_upper_bound;

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
    public GurobiClusterer(List<Node> nodes, List<ClusterEdge> edges, Map<Node, Integer> nodeLoads) {
        this.nodes = nodes;
        this.N = nodes.size();
        this.edges = edges;
        this.nodeLoads = nodeLoads;

        this.K_min = (int) Math.ceil((double) N / N_MAX);
        this.K_max = N / N_MIN;

        // Map JAXB nodes to integers from 0 to N-1 for the Gurobi matrix
        for (int i = 0; i < N; i++) {
            nodeIndex.put(nodes.get(i), i);
        }

        // Extraction of Firewall allocation places
        this.Fw = new int[N];
        int totalAp = 0;
        for (int i = 0; i < N; i++) {
            Node n = nodes.get(i);
            if (n.getFunctionalType() == null) {
                totalAp += 1;
                Fw[i] = 1;
            } else {
                Fw[i] = 0;
            }
        }

        // We can say with certainty that the number of allocation places in a cluster will never be greater than the minimum between the total number of AP and the maximum number of nodes in a cluster
        fw_upper_bound = Math.min(totalAp, N_MAX);

        System.out.println("Gurobi Min-Cut Pre-processing completed:");
        System.out.println(" - Total nodes in the topology : " + N);
        System.out.println(" - Active edges (Weighted Edges) : " + edges.size());
    }

    public void solve_ILP() throws GRBException {
        // Initialization of the Gurobi environment (OutputFlag = 0 to silence verbose logs)
        GRBEnv env = new GRBEnv(true);
        env.set(GRB.IntParam.OutputFlag, 0);
        env.start();
        //env.set(GRB.DoubleParam.MIPGap, 0.02); // Stops when it reaches 98% of the absolute optimum
        // Optional: set an absolute time limit (e.g., 3 minutes)
        //env.set(GRB.DoubleParam.TimeLimit, 180.0);
        GRBModel model = null;

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

            // Symmetry Breaking: force the activation of clusters in sequential order (0, 1, 2...)
            for (int k = 0; k < K_max - 1; k++) {
                model.addConstr(A[k], GRB.GREATER_EQUAL, A[k+1], "Symmetry_Breaking_" + k);
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
            GRBVar maxFlows = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "Max_Flows");
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
            model.addConstr(activeK, GRB.LESS_EQUAL, K_max, "Max_Active_Clusters");

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
                int activeClusterCount = 0;
                for (int k = 0; k < K_max; k++) {
                    if (Math.round(A[k].get(GRB.DoubleAttr.X)) == 1) {
                        activeClusterCount++;
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
                System.out.println("---------------------------------------");
                System.out.println("Total Active Clusters: " + activeClusterCount);
                // This is the value of the objective function
                System.out.println("Objective Value      : " + model.get(GRB.DoubleAttr.ObjVal));
            }
        } finally {
            if (model != null)
                model.dispose();
            env.dispose();
        }
    }
}