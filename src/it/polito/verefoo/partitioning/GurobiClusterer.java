package it.polito.verefoo.partitioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import it.polito.verefoo.jaxb.FunctionalTypes;
import it.polito.verefoo.jaxb.Graph;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.jaxb.Neighbour;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.PName;
import it.polito.verefoo.jaxb.Property;

import com.gurobi.gurobi.*;

public class GurobiClusterer {

    // --- STATIC HYPERPARAMETERS ---
    private static final int N_MAX = 25;
    private static final double LAMBDA_1 = 0.9;
    private static final double LAMBDA_2 = 0.6;
    private static final double LAMBDA_3 = 0.9;
    private static final double LAMBDA_4 = 0.9;

    private NFV nfvRoot;

    // --- ILP Variables ---
    private int N, K_min, K_max;
    private List<Node> nodes;
    private int[] Fw;

    private List<Property> flows_reach;
    private List<Property> flows_isol;
    private List<Property> allFlows;
    // valid flows is needed to iterate through the flows and be sure that f = 0 means "Flow A" when you define the S[f][k] matrix,
    // and f = 0 still means "Flow A" later when you define the Objective Function.
    private List<Property> validFlows;
    private Map<Property, List<Node>> flowPaths;
    Map<Node, Integer> nodeIndex = new HashMap<>();

    // Constructor: Only needs the NFV root!
    public GurobiClusterer(NFV root) {
        this.nfvRoot = root;

        // 1. Extract Nodes and compute limits
        Graph mainGraph = nfvRoot.getGraphs().getGraph().get(0);
        this.nodes = mainGraph.getNode();
        this.N = nodes.size();

        this.K_min = (int) Math.ceil((double) N / N_MAX);
        this.K_max = N / 2;

        for (int i = 0; i < N; i++)
            nodeIndex.put(nodes.get(i), i);
        // 2. Extract Firewall capabilities (Fw[i])
        this.Fw = new int[N];
        for (int i = 0; i < N; i++) {
            Node n = nodes.get(i);
            if (n.getFunctionalType() == FunctionalTypes.FIREWALL) {
                Fw[i] = 1;
            } else {
                Fw[i] = 0;
            }
        }

        // 3. Extract and split Properties (Flows)
        this.flows_reach = new ArrayList<>();
        this.flows_isol = new ArrayList<>();
        this.allFlows = new ArrayList<>();

        for (Property p : nfvRoot.getPropertyDefinition().getProperty()) {
            if (p.getName() == PName.REACHABILITY_PROPERTY) {
                flows_reach.add(p);
            } else if (p.getName() == PName.ISOLATION_PROPERTY) {
                flows_isol.add(p);
            }
            allFlows.add(p);
        }

        // 4. Extract Paths using pure JAXB Allocation Graph topology
        extractPaths();
    }

    private void extractPaths() {
        this.flowPaths = new HashMap<>();
        this.validFlows = new ArrayList<>(); // The clean list

        // Build the O(1) lookup map using pure JAXB nodes
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node n : this.nodes) {
            nodeMap.put(n.getName(), n);
        }

        for (Property p : allFlows) {
            List<Node> pi_function_nodes = computePiFunction(p.getSrc(), p.getDst(), nodeMap);

            if (pi_function_nodes != null && !pi_function_nodes.isEmpty()) {
                this.flowPaths.put(p, pi_function_nodes);
                this.validFlows.add(p);
            } else {
                System.err.println("WARNING: Unreachable flow on Allocation Graph! Src: " + p.getSrc() + " Dst: " + p.getDst());
                //this.flowPaths.put(p, new ArrayList<>());
            }
        }
        System.out.println("Path extraction complete using Allocation Graph topology.");
    }

    /**
     * Implements the Pi function using pure JAXB Topology.
     * Guaranteed to include dummy nodes because AGG updated the JAXB Neighbour lists.
     */
    private List<Node> computePiFunction(String srcName, String dstName, Map<String, Node> nodeMap) {
        if (!nodeMap.containsKey(srcName)) return null;

        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        parent.put(srcName, null);
        queue.add(srcName);

        while (!queue.isEmpty()) {
            String currentName = queue.poll();

            // Did we reach the destination?
            if (currentName.equals(dstName)) {
                List<Node> path = new LinkedList<>();
                String step = currentName;

                // Backtrack to build the path
                while (step != null) {
                    path.add(0, nodeMap.get(step));
                    step = parent.get(step);
                }

                // Pi Function: Remove the source node
                if (!path.isEmpty()) path.remove(0);
                return path;
            }

            // Look up the current JAXB Node
            Node currentNode = nodeMap.get(currentName);
            if (currentNode == null) continue;

            // Iterate over JAXB Neighbours
            for (Neighbour nb : currentNode.getNeighbour()) {
                String nbName = nb.getName();
                if (!parent.containsKey(nbName)) {
                    parent.put(nbName, currentName);
                    queue.add(nbName);
                }
            }
        }
        return null; // Return null if unreachable
    }

    public void solve_ILP() throws GRBException {
        // Create gurobi environment, start it and create a new model
        GRBEnv   env   = new GRBEnv(true);
        env.set(GRB.IntParam.OutputFlag, 0);   // set to 0 to silence Gurobi logs

        env.start();
        GRBModel model = null;

        try {
            model = new GRBModel(env);

            int numFlows = validFlows.size();

            // Decision Variables
            // assert X[i][k] = 0 or X[i][k] = 1 for i in nodes , k in clusters
            GRBVar X[][] = new GRBVar[N][K_max];
            for (int i = 0; i < N; i++){
                for (int k = 0; k < K_max; k++) {
                    X[i][k] = model.addVar(0.0,1.0,0.0, GRB.BINARY, "X_" + i + "_" + k);
                }
            }
            // assert A[k] = 0 or A[k] = 1 for k in clusters
            GRBVar A[] = new GRBVar[K_max];
            for (int k = 0; k < K_max; k++){
                A[k] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "A_" + k);
            }
            // assert S[f][k] = 0 or S[f][k] = 1 for f in flows , k in clusters
            GRBVar S[][] = new GRBVar[numFlows][K_max];
            // C [f] >= 1 for f in flows
            GRBVar C[] = new GRBVar[numFlows];
            for (int f = 0; f < numFlows; f++) {
                C[f] = model.addVar(1.0, GRB.INFINITY, 0.0, GRB.INTEGER, "C_" + f);
                for (int k = 0; k < K_max; k++) {
                    S[f][k] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "S_" + f + "_" + k);
                }
            }
            // Max_Flows >= 0; Max_Fw >= 0
            GRBVar maxFlows = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "Max_Flows");
            GRBVar maxFw = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "Max_Fw");

            // Constraints
            // Each node in exactly one cluster and linked to the activation A[k]
            for (int i = 0; i < N; i++){
                GRBLinExpr sumK = new GRBLinExpr();
                for (int k = 0; k < K_max; k++) {
                    sumK.addTerm(1.0, X[i][k]);
                    model.addConstr(X[i][k], GRB.LESS_EQUAL, A[k], "Link_X_A_" + i + "_" + k);
                }
                model.addConstr(sumK, GRB.EQUAL, 1.0, "One_Cluster_" + i);
            }
            // Cluster dimension
            for (int k = 0; k < K_max; k++) {
                GRBLinExpr sizeK = new GRBLinExpr();
                for (int i = 0; i < N; i++) {
                    sizeK.addTerm(1.0, X[i][k]);
                }
                GRBLinExpr minSize = new GRBLinExpr();
                minSize.addTerm(2.0, A[k]);
                model.addConstr(sizeK, GRB.GREATER_EQUAL, minSize, "Min_Size_" + k);

                GRBLinExpr maxSize = new GRBLinExpr();
                maxSize.addTerm(N_MAX, A[k]);
                model.addConstr(sizeK, GRB.LESS_EQUAL, maxSize, "Max_Size_" + k);
            }
            // Number of active clusters
            GRBLinExpr activeK = new GRBLinExpr();
            for (int k = 0; k < K_max; k++) {
                activeK.addTerm(1.0, A[k]);
            }
            model.addConstr(activeK, GRB.GREATER_EQUAL, K_min, "Min_Active_Clusters");
            model.addConstr(activeK, GRB.LESS_EQUAL, K_max, "Max_Active_Clusters");
            // S[f][k] linked to X
            for (int f = 0; f < numFlows; f++) {
                Property prop = validFlows.get(f);
                List<Node> pathNodes = flowPaths.get(prop);

                for (int k = 0; k < K_max; k++) {
                    GRBLinExpr cover = new GRBLinExpr();
                    for (Node n_i : pathNodes) {
                        int i = nodeIndex.getOrDefault(n_i, -1);
                        if (i != -1) {
                            model.addConstr(S[f][k], GRB.GREATER_EQUAL, X[i][k], "S_lb_" + f + "_" + k + "_" + i);
                            cover.addTerm(1.0, X[i][k]);
                        }
                    }
                    model.addConstr(S[f][k], GRB.LESS_EQUAL, cover, "S_ub_" + f + "_" + k);
                }
            }
            // C[f] definition
            for (int f = 0; f < numFlows; f++) {
                GRBLinExpr sumS = new GRBLinExpr();
                for (int k = 0; k < K_max; k++) {
                    sumS.addTerm(1.0, S[f][k]);
                }
                model.addConstr(C[f], GRB.EQUAL, sumS, "Def_C_" + f);
            }

            // balancing flows and clusters
            for (int k = 0; k < K_max; k++) {
                GRBLinExpr flowsK = new GRBLinExpr();
                for (int f = 0; f < numFlows; f++) {
                    flowsK.addTerm(1.0, S[f][k]);
                }
                model.addConstr(maxFlows, GRB.GREATER_EQUAL, flowsK, "Max_Flows_Bound_" + k);

                GRBLinExpr fwK = new GRBLinExpr();
                for (int i = 0; i < N; i++) {
                    if (Fw[i] == 1) {
                        fwK.addTerm(1.0, X[i][k]);
                    }
                }
                model.addConstr(maxFw, GRB.GREATER_EQUAL, fwK, "Max_Fw_Bound_" + k);
            }

            // Objective function
            GRBLinExpr objective = new GRBLinExpr();

            for (int f = 0; f < numFlows; f++) {
                Property prop = validFlows.get(f);
                double lambda = (prop.getName() == PName.ISOLATION_PROPERTY) ? LAMBDA_1 : LAMBDA_2;

                objective.addTerm(lambda, C[f]);
                objective.addConstant(-lambda);
            }

            objective.addTerm(LAMBDA_3, maxFlows);
            objective.addTerm(LAMBDA_4, maxFw);

            model.setObjective(objective, GRB.MINIMIZE);

            // Execute solver
            model.write("my_math_model.lp");
            model.optimize();

            if (model.get(GRB.IntAttr.Status) == GRB.INFEASIBLE) {
                System.err.println("Model is Infeasible. Check limits.");
            } else {
                System.out.println("--- ILP CLUSTERING RESULT ---");
                int activeClusterCount = 0;
                for (int k = 0; k < K_max; k++) {
                    if (Math.round(A[k].get(GRB.DoubleAttr.X)) == 1) {
                        activeClusterCount++;
                        System.out.println("\n--- CLUSTER " + k + " ---");

                        List<String> clusterNodes = new ArrayList<>();
                        int fwCount = 0;

                        for (int i = 0; i < N; i++) {
                            if (Math.round(X[i][k].get(GRB.DoubleAttr.X)) == 1) {
                                Node n = nodes.get(i);
                                clusterNodes.add(n.getName());
                                if (Fw[i] == 1) fwCount++;
                            }
                        }

                        int flowCount = 0;
                        for (int f = 0; f < numFlows; f++) {
                            if (Math.round(S[f][k].get(GRB.DoubleAttr.X)) == 1) flowCount++;
                        }

                        System.out.println("Nodes (" + clusterNodes.size() + "): " + clusterNodes);
                        System.out.println("Firewalls Available : " + fwCount);
                        System.out.println("Flows Traversed     : " + flowCount);
                    }
                }
                System.out.println("Total Active Clusters: " + activeClusterCount);
            }
        } finally {
            if (model != null)
                model.dispose();
            env.dispose();
        }

    }
}
