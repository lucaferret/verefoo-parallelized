package it.polito.verefoo.extra;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import it.polito.verefoo.jaxb.*;

public class TestCaseGeneratorTexas2000 {
    NFV nfv;
    String name;

    Random rand = null;

    Set<String> allIPs;

    private int policyPairCallCount = 0;

    //add isolationNumber: -1 unbound, usare multipli di reachability number
    public TestCaseGeneratorTexas2000(int seed, int numberUCC, int numberSS, boolean withPorts, boolean withIsolation, int isolationBound) {
        // Validate parameter consistency
        if (!withIsolation && isolationBound != -1) {
            System.err.println("ERROR: Parameter inconsistency!");
            System.err.println("withIsolation=false but isolationBound=" + isolationBound + " (should be -1 when withIsolation=false)");
            throw new IllegalArgumentException("Cannot specify isolationBound when withIsolation is false");
        }

        this.name = "Texas2000";
        this.rand = new Random(seed);

        allIPs = new HashSet<String>();
        nfv = generateNFV(numberUCC, numberSS, withPorts, withIsolation, isolationBound);
    }

    private PName pickReachabilityTypeEvery5() {
        int mod = policyPairCallCount % 5;   // 0..4
        policyPairCallCount++;
        return (mod < 3) ?  PName.REACHABILITY_PROPERTY: PName.COMPLETE_REACHABILITY_PROPERTY;
    }

    private String createIP() {
        String ip;
        int first, second, third, forth;
        first = rand.nextInt(256);
        if (first == 0)
            first++;
        second = rand.nextInt(256);
        third = rand.nextInt(256);
        forth = rand.nextInt(256);
        ip = new String(first + "." + second + "." + third + "." + forth);
        if (rand.nextBoolean()) {
            if (rand.nextBoolean())
                ip = new String(first + "." + first + "." + first + "." + first);
            else {
                if (rand.nextBoolean())
                    ip = new String(second + "." + second + "." + second + "." + second);
                else {
                    ip = new String(third + "." + third + "." + third + "." + third);
                }
            }
        }
        return ip;
    }

    private String createRandomIP() {
        boolean notCreated = true;
        String ip = null;
        while (notCreated) {
            ip = createIP();
            if (!allIPs.contains(ip)) {
                notCreated = false;
                allIPs.add(ip);
            }
        }
        return ip;
    }

    private void createPolicy(PName type, NFV nfv, Graph graph, String IPClient, String IPServer, L4ProtocolTypes lv4Proto, String srcPort, String dstPort) {

        Property property = new Property();
        property.setName(type);
        property.setGraph((long) 0);
        property.setSrc(IPClient);
        property.setDst(IPServer);
        property.setLv4Proto(lv4Proto);
        property.setSrcPort(srcPort);
        property.setDstPort(dstPort);
        nfv.getPropertyDefinition().getProperty().add(property);
    }

    private void createPolicyPair(NFV nfv, Graph graph, String src, String dst, L4ProtocolTypes proto, String port, boolean withPorts) {
        PName chosenType = pickReachabilityTypeEvery5();

        String targetPort = withPorts ? port : "*";
        createPolicy(chosenType, nfv, graph, src, dst, proto, targetPort, "*");
        createPolicy(chosenType, nfv, graph, dst, src, proto, targetPort, "*");
    }


    /**
     * Creates an isolation policy between two nodes
     */
    private void createIsolationPolicies(NFV nfv, Graph graph, int isolationBound) {
        // Collect all webclient and webserver nodes from the graph
        List<String> webClientAndServerNodes = new ArrayList<>();
        Map<String, FunctionalTypes> nodeTypeMap = new HashMap<>();

        for (Node node : graph.getNode()) {
            FunctionalTypes nodeType = node.getFunctionalType();
            nodeTypeMap.put(node.getName(), nodeType);

            if (nodeType == FunctionalTypes.WEBCLIENT || nodeType == FunctionalTypes.WEBSERVER) {
                webClientAndServerNodes.add(node.getName());
            }
        }

        // Collect all (src, dst) pairs that have reachability properties
        Set<String> reachabilityPairs = new HashSet<>();

        for (Property property : nfv.getPropertyDefinition().getProperty()) {
            if (property.getName() == PName.REACHABILITY_PROPERTY || property.getName() == PName.COMPLETE_REACHABILITY_PROPERTY ) {
                String src = property.getSrc();
                String dst = property.getDst();
                // Add both directions as strings
                reachabilityPairs.add(src + "->" + dst);
                reachabilityPairs.add(dst + "->" + src);
            }
        }

        // Collect all node pairs that are direct neighbors
        Set<String> neighborPairs = new HashSet<>();
        for (Node node : graph.getNode()) {
            String nodeName = node.getName();
            for (Neighbour neighbor : node.getNeighbour()) {
                String neighborName = neighbor.getName();
                // Add both directions for neighbors
                neighborPairs.add(nodeName + "->" + neighborName);
                neighborPairs.add(neighborName + "->" + nodeName);
            }
        }

        // Generate all possible webclient/webserver node pairs and create isolation policies
        int isolationCount = 0;
        int skippedNotWebNode = 0;
        int skippedReachability = 0;
        int skippedNeighbor = 0;
        int skippedNoFirewall = 0;
        int skippedBoundReached = 0;

        // System.out.println("Isolation bound: " + (isolationBound == -1 ? "unlimited" : isolationBound));


        // Build candidate pairs (src->dst) among web nodes (excluding self)
        List<String[]> candidates = new ArrayList<>();

        for (String srcNode : webClientAndServerNodes) {
            for (String dstNode : webClientAndServerNodes) {
                if (!srcNode.equals(dstNode)) {
                    candidates.add(new String[]{srcNode, dstNode});
                }
            }
        }

        // Randomize order (optionally seed for reproducibility)
        Collections.shuffle(candidates, new Random()); // or new Random(seed)

        for (String[] pair : candidates) {
            String srcNode = pair[0];
            String dstNode = pair[1];
            String currentPairKey = srcNode + "->" + dstNode;

            // Stop when bound reached (if bounded)
            if (isolationBound != -1 && isolationCount >= isolationBound) {
                break;
            }

            // Skip if pair already has reachability (make sure you include COMPLETE too!)
            if (reachabilityPairs.contains(currentPairKey)) {
                skippedReachability++;
                continue;
            }

            // Skip if direct neighbors
            if (neighborPairs.contains(currentPairKey)) {
                skippedNeighbor++;
                continue;
            }

            // Skip if no firewall in path
            if (!hasFirewallInPath(graph, srcNode, dstNode)) {
                skippedNoFirewall++;
                continue;
            }

            // Otherwise create isolation property
            Property isolationProperty = new Property();
            isolationProperty.setName(PName.ISOLATION_PROPERTY);
            isolationProperty.setGraph(0L);
            isolationProperty.setSrc(srcNode);
            isolationProperty.setDst(dstNode);
            isolationProperty.setLv4Proto(L4ProtocolTypes.ANY);
            isolationProperty.setSrcPort("*");
            isolationProperty.setDstPort("*");

            nfv.getPropertyDefinition().getProperty().add(isolationProperty);
            isolationCount++;
        }

        // Conta anche i nodi non-web che vengono esclusi dal primo filtro
        for (Node node : graph.getNode()) {
            if (node.getFunctionalType() != FunctionalTypes.WEBCLIENT &&
                    node.getFunctionalType() != FunctionalTypes.WEBSERVER) {
                skippedNotWebNode++;
            }
        }

        // System.out.println("Created " + isolationCount + " isolation properties");
        // System.out.println("Skipped " + skippedNotWebNode + " nodes (not webclient/webserver)");
        // System.out.println("Skipped " + skippedReachability + " pairs due to existing reachability");
        // System.out.println("Skipped " + skippedNeighbor + " pairs due to being neighbors");
        // System.out.println("Skipped " + skippedNoFirewall + " pairs due to no firewall in path");
        if (isolationBound != -1) {
            // System.out.println("Skipped " + skippedBoundReached + " pairs due to bound limit reached");
        }
        // System.out.println("Total pairs skipped: " + (skippedReachability + skippedNeighbor + skippedNoFirewall + skippedBoundReached));
    }

    // Metodo helper per verificare se c'è almeno un firewall nel percorso tra due nodi
    private boolean hasFirewallInPath(Graph graph, String srcNode, String dstNode) {
        // Map to quickly find nodes by name
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : graph.getNode()) {
            nodeMap.put(node.getName(), node);
        }

        // BFS per trovare un percorso che contenga almeno un firewall
        Set<String> visited = new HashSet<>();              //tiene traccia dei nodi già visitati per evitare cicli infiniti
        Map<String, String> parent = new HashMap<>();       //mappa che registra da quale nodo siamo arrivati a ogni nodo (per ricostruire il percorso)
        List<String> queue = new ArrayList<>();             //lista che funge da coda

        queue.add(srcNode);                                 //Aggiunge il nodo sorgente alla coda
        visited.add(srcNode);                               //Lo marca come visitato
        parent.put(srcNode, null);                    //Imposta il suo parent a null (perché è il punto di partenza)

        while (!queue.isEmpty()) {
            String current = queue.remove(0);

            if (current.equals(dstNode)) {
                // Path found, check if it contains at least one firewall
                return pathContainsFirewall(parent, srcNode, dstNode, nodeMap);
            }

            // Get current node and explore its neighbors
            Node currentNode = nodeMap.get(current);
            if (currentNode != null) {
                for (Neighbour neighbor : currentNode.getNeighbour()) {
                    String neighborName = neighbor.getName();
                    if (!visited.contains(neighborName)) {
                        visited.add(neighborName);
                        parent.put(neighborName, current);
                        queue.add(neighborName);
                    }
                }
            }
        }

        // If loop exits without finding destination, no path exists
        return false;
    }

    /**
     * Helper method to check if a path contains at least one firewall
     */
    private boolean pathContainsFirewall(Map<String, String> parent, String srcNode, String dstNode, Map<String, Node> nodeMap) {
        String current = dstNode;

        // Traverse path from destination back to source, checking for firewalls
        while (current != null && !current.equals(srcNode)) {
            Node node = nodeMap.get(current);
            if (node != null && node.getFunctionalType() == null) {
                return true;
            }
            current = parent.get(current);
        }

        // Also check source node
        Node srcNodeObj = nodeMap.get(srcNode);
        if (srcNodeObj != null && srcNodeObj.getFunctionalType() == null) {
            return true;
        }

        return false;
    }

    public NFV generateNFV(int numberUCC, int numberSS, boolean withPorts, boolean withIsolation, int isolationBound) {
        // Array containing UCC-SS connection firewalls
        Node[] ucc_firewall = new Node[numberUCC];

        // Nodes for policy creation
        String[] dnp3_master = new String[numberUCC];
        String[] dnp3_os = new String[numberSS];
        String[] vendor = new String[numberUCC];
        String[] pubdmz_webserver = new String[numberUCC];
        String[] pubdmz_database = new String[numberUCC];
        String[] ba_iccp_node = new String[numberUCC];
        String[] subdmz_local_data_historian = new String[numberSS];
        String[] corporate_dmz_utility = new String[numberUCC];

        NFV nfv = new NFV();
        Graphs graphs = new Graphs();
        PropertyDefinition pd = new PropertyDefinition();
        Constraints cnst = new Constraints();
        NodeConstraints nc = new NodeConstraints();
        LinkConstraints lc = new LinkConstraints();
        cnst.setNodeConstraints(nc);
        cnst.setLinkConstraints(lc);
        nfv.setGraphs(graphs);
        nfv.setPropertyDefinition(pd);
        nfv.setConstraints(cnst);
        Graph graph = new Graph();
        graph.setServiceGraph(false);
        graph.setId((long) 0);

        // Creation of Balancing Authority [BA]

        // [BA] - [ICCP Server]
        String ip_iccp_server = createRandomIP();
        Node iccp_server = new Node();
        iccp_server.setFunctionalType(FunctionalTypes.WEBSERVER);
        iccp_server.setName(ip_iccp_server);
        Configuration confS = new Configuration();
        confS.setName("confB");
        confS.setDescription("[ICCP Server]");
        Webserver iccp_ws = new Webserver();
        iccp_ws.setName(iccp_server.getName());
        confS.setWebserver(iccp_ws);
        iccp_server.setConfiguration(confS);

        // [BA] - [Router]
        String ip_ba_router = createRandomIP();
        Node ba_router = new Node();
        ba_router.setFunctionalType(FunctionalTypes.FORWARDER);
        ba_router.setName(ip_ba_router);

        // [BA] - [Firewall]
        String ip_ba_firewall = createRandomIP();
        Node ba_firewall = new Node();
        ba_firewall.setName(ip_ba_firewall);

        // [BA]: Attach ICCP Server to Router
        Neighbour ba_router_neighbour = new Neighbour();
        ba_router_neighbour.setName(ba_router.getName());
        iccp_server.getNeighbour().add(ba_router_neighbour);
        Neighbour iccp_server_neighbour = new Neighbour();
        iccp_server_neighbour.setName(iccp_server.getName());
        ba_router.getNeighbour().add(iccp_server_neighbour);

        // [BA]: Attach Firewall to Router
        Neighbour ba_firewall_neighbour = new Neighbour();
        ba_firewall_neighbour.setName(ba_firewall.getName());
        ba_router.getNeighbour().add(ba_firewall_neighbour);
        Neighbour firewall_router_neighbour = new Neighbour();
        firewall_router_neighbour.setName(ba_router.getName());
        ba_firewall.getNeighbour().add(firewall_router_neighbour);

        // Aggiungo manualmete tutto al graph, andrà fatta funzione per aggiungere i
        // nodi
        graph.getNode().add(iccp_server);
        graph.getNode().add(ba_router);
        graph.getNode().add(ba_firewall);

        // Creation of Unity Control Centers [UCC]
        for (int i = 0; i < numberUCC; ++i) {
            // [UCC]: From BA Firewall
            String ip_ucc_firewall = createRandomIP();
            Node ucc_firewall0 = new Node();
            ucc_firewall0.setName(ip_ucc_firewall);
            Configuration confU = new Configuration();
            confU.setName("confU" + i);
            confU.setDescription("[UCC Firewall_" + i + "]");

            Neighbour ucc_firewall0_neighbour = new Neighbour();
            ucc_firewall0_neighbour.setName(ucc_firewall0.getName());
            ba_firewall.getNeighbour().add(ucc_firewall0_neighbour);
            ucc_firewall0.getNeighbour().add(ba_firewall_neighbour);

            // [UCC]: Router0
            String ip_ucc_router0 = createRandomIP();
            Node ucc_router0 = new Node();
            ucc_router0.setFunctionalType(FunctionalTypes.FORWARDER);
            ucc_router0.setName(ip_ucc_router0);
            // Attach Router0 to Firewall
            Neighbour ucc_router0_neighbour = new Neighbour();
            ucc_router0_neighbour.setName(ucc_router0.getName());
            ucc_firewall0.getNeighbour().add(ucc_router0_neighbour);
            // Attach Firewall to Router0
            ucc_router0.getNeighbour().add(ucc_firewall0_neighbour);

            // [UCC]: SCADA DMZ Switch
            String ip_ucc_switch = createRandomIP();
            Node ucc_switch = new Node();
            ucc_switch.setFunctionalType(FunctionalTypes.FORWARDER);
            ucc_switch.setName(ip_ucc_switch);
            // Attach Switch to Router0
            Neighbour ucc_switch_neighbour = new Neighbour();
            ucc_switch_neighbour.setName(ucc_switch.getName());
            ucc_router0.getNeighbour().add(ucc_switch_neighbour);
            // Attach Router0 to Switch
            ucc_switch.getNeighbour().add(ucc_router0_neighbour);

            // [UCC]: EMS (Energy Management System). -- DNP3 Master
            String ip_DNP3_Master = createRandomIP();
            Node DNP3_Master = new Node();
            DNP3_Master.setFunctionalType(FunctionalTypes.WEBSERVER);
            DNP3_Master.setName(ip_DNP3_Master);
            Configuration confDNP3_Master = new Configuration();
            confDNP3_Master.setName("confDNP3_Master" + i);
            confDNP3_Master.setDescription("[UCC - SCADA DMZ - DNP3_Master_" + i + "]");
            Webserver DNP3_Master_ws = new Webserver();
            DNP3_Master_ws.setName(DNP3_Master.getName());
            confDNP3_Master.setWebserver(DNP3_Master_ws);
            DNP3_Master.setConfiguration(confDNP3_Master);
            // Attach DNP3_Master to Switch
            Neighbour DPN3_Master_neighbour = new Neighbour();
            DPN3_Master_neighbour.setName(DNP3_Master.getName());
            ucc_switch.getNeighbour().add(DPN3_Master_neighbour);
            // Attach Switch to DNP3_Master
            DNP3_Master.getNeighbour().add(ucc_switch_neighbour);
            // Add DNP3_Master to the DNP3 Policy
            dnp3_master[i] = ip_DNP3_Master;


            // [UCC]: EMS -- Scada PI Server
            String ip_Scada_PI_Server = createRandomIP();
            Node Scada_PI_server = new Node();
            Scada_PI_server.setFunctionalType(FunctionalTypes.WEBSERVER);
            Scada_PI_server.setName(ip_Scada_PI_Server);
            Configuration confScada_PI = new Configuration();
            confScada_PI.setName("confScada_PI" + i);
            confScada_PI.setDescription("[UCC - SCADA DMZ - Scada_PI_Server_" + i + "]");
            Webserver Scada_PI_ws = new Webserver();
            Scada_PI_ws.setName(Scada_PI_server.getName());
            confScada_PI.setWebserver(Scada_PI_ws);
            Scada_PI_server.setConfiguration(confScada_PI);
            // Attach Scada_PI_Server to Switch
            Neighbour Scada_PI_neighbour = new Neighbour();
            Scada_PI_neighbour.setName(Scada_PI_server.getName());
            ucc_switch.getNeighbour().add(Scada_PI_neighbour);
            // Attach Switch to Scada_PI_Server
            Scada_PI_server.getNeighbour().add(ucc_switch_neighbour);

            // [UCC]: EMS -- HMI
            String ip_HMI = createRandomIP();
            Node HMI = new Node();
            HMI.setFunctionalType(FunctionalTypes.WEBSERVER);
            HMI.setName(ip_HMI);
            Configuration confHMI = new Configuration();
            confHMI.setName("confHMI" + i);
            confHMI.setDescription("[UCC - SCADA DMZ - HMI_" + i + "]");
            Webserver HMI_ws = new Webserver();
            HMI_ws.setName(HMI.getName());
            confHMI.setWebserver(HMI_ws);
            HMI.setConfiguration(confHMI);
            // Attach HMI to Switch
            Neighbour HMI_neighbour = new Neighbour();
            HMI_neighbour.setName(HMI.getName());
            ucc_switch.getNeighbour().add(HMI_neighbour);
            // Attach Switch to HMI
            HMI.getNeighbour().add(ucc_switch_neighbour);

            // [UCC]: Router1
            String ip_ucc_router1 = createRandomIP();
            Node ucc_router1 = new Node();
            ucc_router1.setFunctionalType(FunctionalTypes.FORWARDER);
            ucc_router1.setName(ip_ucc_router1);
            // Attach Router1 to Switch
            Neighbour ucc_router1_neighbour = new Neighbour();
            ucc_router1_neighbour.setName(ucc_router1.getName());
            ucc_switch.getNeighbour().add(ucc_router1_neighbour);
            // Attach Switch to Router1
            ucc_router1.getNeighbour().add(ucc_switch_neighbour);

            // [UCC]: Firewall1
            String ip_ucc_firewall1 = createRandomIP();
            Node ucc_firewall1 = new Node();
            ucc_firewall1.setName(ip_ucc_firewall1);

            // Attach Firewall1 to Router1
            Neighbour ucc_firewall1_neightbour = new Neighbour();
            ucc_firewall1_neightbour.setName(ucc_firewall1.getName());
            ucc_router1.getNeighbour().add(ucc_firewall1_neightbour);
            // Attach Router1 to Firewall1
            ucc_firewall1.getNeighbour().add(ucc_router1_neighbour);
            // Add Firewall1 to the array of UCC firewalls
            ucc_firewall[i] = ucc_firewall1;

            // [UCC]: PUBLIC DMZ SWITCH
            String ip_ucc_pubdmz_switch = createRandomIP();
            Node ucc_switch_pubdmz = new Node();
            ucc_switch_pubdmz.setFunctionalType(FunctionalTypes.FORWARDER);
            ucc_switch_pubdmz.setName(ip_ucc_pubdmz_switch);
            // Attach DMZ Switch to Firewall1
            Neighbour ucc_switch_pubdmz_neighbour = new Neighbour();
            ucc_switch_pubdmz_neighbour.setName(ucc_switch_pubdmz.getName());
            ucc_firewall0.getNeighbour().add(ucc_switch_pubdmz_neighbour);
            // Attach Firewall0 to DMZ Switch
            ucc_switch_pubdmz.getNeighbour().add(ucc_firewall0_neighbour);

            // [UCC]: PUBLIC DMZ WEBSERVER
            String ip_ucc_dmz_webserver = createRandomIP();
            Node ucc_pubdmz_webserver = new Node();
            ucc_pubdmz_webserver.setFunctionalType(FunctionalTypes.WEBSERVER);
            ucc_pubdmz_webserver.setName(ip_ucc_dmz_webserver);
            Configuration confDMZ = new Configuration();
            confDMZ.setName("confDMZ" + i);
            confDMZ.setDescription("[UCC - PUBLIC DMZ Webserver_" + i + "]");
            Webserver ucc_dmz_ws = new Webserver();
            ucc_dmz_ws.setName(ucc_pubdmz_webserver.getName());
            confDMZ.setWebserver(ucc_dmz_ws);
            ucc_pubdmz_webserver.setConfiguration(confDMZ);
            // Attach DMZ Webserver to PUBLIC DMZ SWITCH
            Neighbour ucc_dmz_webserver_neighbour = new Neighbour();
            ucc_dmz_webserver_neighbour.setName(ucc_pubdmz_webserver.getName());
            ucc_switch_pubdmz.getNeighbour().add(ucc_dmz_webserver_neighbour);
            // Attach PUBLIC DMZ SWITCH to DMZ Webserver
            ucc_pubdmz_webserver.getNeighbour().add(ucc_switch_pubdmz_neighbour);
            // Add DMZ Webserver to the HTTPS Policy
            pubdmz_webserver[i] = ip_ucc_dmz_webserver;

            // [UCC]: PUBLIC DMZ Database
            String ip_ucc_pubdmz_database = createRandomIP();
            Node ucc_pubdmz_database = new Node();
            ucc_pubdmz_database.setFunctionalType(FunctionalTypes.WEBSERVER);
            ucc_pubdmz_database.setName(ip_ucc_pubdmz_database);
            Configuration confPubDMZ = new Configuration();
            confPubDMZ.setName("confPubDMZ" + i);
            confPubDMZ.setDescription("[UCC - PUBLIC DMZ Database_" + i + "]");
            Webserver ucc_pubdmz_db = new Webserver();
            ucc_pubdmz_db.setName(ucc_pubdmz_database.getName());
            confPubDMZ.setWebserver(ucc_pubdmz_db);
            ucc_pubdmz_database.setConfiguration(confPubDMZ);
            // Attach PUBLIC DMZ Database to PUBLIC DMZ SWITCH
            Neighbour ucc_pubdmz_db_neighbour = new Neighbour();
            ucc_pubdmz_db_neighbour.setName(ucc_pubdmz_database.getName());
            ucc_switch_pubdmz.getNeighbour().add(ucc_pubdmz_db_neighbour);
            // Attach PUBLIC DMZ SWITCH to DMZ Database
            ucc_pubdmz_database.getNeighbour().add(ucc_switch_pubdmz_neighbour);
            // Add DMZ Database to the policy
            pubdmz_database[i] = ip_ucc_pubdmz_database;

            // [UCC]: PUBLIC DMZ Router2
            String ip_ucc_dmz_router2 = createRandomIP();
            Node ucc_dmz_router2 = new Node();
            ucc_dmz_router2.setFunctionalType(FunctionalTypes.FORWARDER);
            ucc_dmz_router2.setName(ip_ucc_dmz_router2);
            // Attach DMZ Router2 to PUBLIC DMZ SWITCH
            Neighbour ucc_dmz_router2_neighbour = new Neighbour();
            ucc_dmz_router2_neighbour.setName(ucc_dmz_router2.getName());
            ucc_switch_pubdmz.getNeighbour().add(ucc_dmz_router2_neighbour);
            // Attach PUBLIC DMZ SWITCH to DMZ Router2
            ucc_dmz_router2.getNeighbour().add(ucc_switch_pubdmz_neighbour);

            // [UCC]: PUBLIC DMZ Firewall2
            String ip_ucc_dmz_firewall2 = createRandomIP();
            Node ucc_dmz_firewall2 = new Node();
            ucc_dmz_firewall2.setName(ip_ucc_dmz_firewall2);

            // Attach DMZ Firewall2 to DMZ Router2
            Neighbour ucc_dmz_firewall2_neighbour = new Neighbour();
            ucc_dmz_firewall2_neighbour.setName(ucc_dmz_firewall2.getName());
            ucc_dmz_router2.getNeighbour().add(ucc_dmz_firewall2_neighbour);
            // Attach DMZ Router2 to DMZ Firewall2
            ucc_dmz_firewall2.getNeighbour().add(ucc_dmz_router2_neighbour);

            // [UCC]: Vendor DMZ - Vendor Webserver
            String ip_ucc_vendor_dmz_webserver = createRandomIP();
            Node ucc_vendor_dmz_webserver = new Node();
            ucc_vendor_dmz_webserver.setFunctionalType(FunctionalTypes.WEBSERVER);
            ucc_vendor_dmz_webserver.setName(ip_ucc_vendor_dmz_webserver);
            Configuration confVendorDMZ = new Configuration();
            confVendorDMZ.setName("confVendorDMZ" + i);
            confVendorDMZ.setDescription("[UCC - Vendor DMZ Webserver_" + i + "]");
            Webserver ucc_vendor_dmz_ws = new Webserver();
            ucc_vendor_dmz_ws.setName(ucc_vendor_dmz_webserver.getName());
            confVendorDMZ.setWebserver(ucc_vendor_dmz_ws);
            ucc_vendor_dmz_webserver.setConfiguration(confVendorDMZ);
            // Attach Vendor DMZ Webserver to DMZ Firewall2
            Neighbour ucc_vendor_dmz_webserver_neighbour = new Neighbour();
            ucc_vendor_dmz_webserver_neighbour.setName(ucc_vendor_dmz_webserver.getName());
            ucc_dmz_firewall2.getNeighbour().add(ucc_vendor_dmz_webserver_neighbour);
            // Attach DMZ Firewall2 to Vendor DMZ Webserver
            ucc_vendor_dmz_webserver.getNeighbour().add(ucc_dmz_firewall2_neighbour);
            // Add Vendor DMZ Webserver to the HTTPS Policy
            vendor[i] = ip_ucc_vendor_dmz_webserver;

            // [UCC]: Corporate DMZ - Utility Partner
            String ip_ucc_corporate_dmz_utility = createRandomIP();
            Node ucc_corporate_dmz_webserver = new Node();
            ucc_corporate_dmz_webserver.setFunctionalType(FunctionalTypes.WEBSERVER);
            ucc_corporate_dmz_webserver.setName(ip_ucc_corporate_dmz_utility);
            Configuration confCorporateDMZUtility = new Configuration();
            confCorporateDMZUtility.setName("confCorporateDMZUtility" + i);
            confCorporateDMZUtility.setDescription("[UCC - Corporate DMZ - Utility Partner_" + i + "]");
            Webserver ucc_corporate_dmz_ws = new Webserver();
            ucc_corporate_dmz_ws.setName(ucc_corporate_dmz_webserver.getName());
            confCorporateDMZUtility.setWebserver(ucc_corporate_dmz_ws);
            ucc_corporate_dmz_webserver.setConfiguration(confCorporateDMZUtility);
            // Attach UCC Corporate DMZ - Utility Partner to DMZ Firewall2
            Neighbour ucc_corporate_dmz_utility_neighbour = new Neighbour();
            ucc_corporate_dmz_utility_neighbour.setName(ucc_corporate_dmz_webserver.getName());
            ucc_dmz_firewall2.getNeighbour().add(ucc_corporate_dmz_utility_neighbour);
            // Attach DMZ Firewall2 to UCC Corporate DMZ - Utility Partner
            ucc_corporate_dmz_webserver.getNeighbour().add(ucc_dmz_firewall2_neighbour);
            // Add UCC Corporate DMZ - Utility Partner to the policy
            corporate_dmz_utility[i] = ip_ucc_corporate_dmz_utility;

            // [UCC]: Internet
            // TODO: Implement Internet Node and Configuration


            // TODO: Implement Corporate DMZ - Utility Partner Node and Configuration

            // [UCC]: BA DMZ - BA ICCP Node
            String ip_badmz_iccp_node = createRandomIP();
            Node ucc_badmz_iccp_node = new Node();
            ucc_badmz_iccp_node.setFunctionalType(FunctionalTypes.WEBSERVER);
            ucc_badmz_iccp_node.setName(ip_badmz_iccp_node);
            Configuration confBAICCP = new Configuration();
            confBAICCP.setName("confBAICCP" + i);
            confBAICCP.setDescription("[UCC - BA DMZ - BA ICCP Node_" + i + "]");
            Webserver ucc_badmz_iccp_ws = new Webserver();
            ucc_badmz_iccp_ws.setName(ucc_badmz_iccp_node.getName());
            confBAICCP.setWebserver(ucc_badmz_iccp_ws);
            ucc_badmz_iccp_node.setConfiguration(confBAICCP);
            // Attach BA DMZ ICCP Node to DMZ Firewall0
            Neighbour ucc_badmz_iccp_neighbour = new Neighbour();
            ucc_badmz_iccp_neighbour.setName(ucc_badmz_iccp_node.getName());
            ucc_firewall0.getNeighbour().add(ucc_badmz_iccp_neighbour);
            // Attach DMZ Firewall0 to BA DMZ ICCP Node
            ucc_badmz_iccp_node.getNeighbour().add(ucc_firewall0_neighbour);
            // Add BA DMZ ICCP Node to policy
            ba_iccp_node[i] = ip_badmz_iccp_node;

            graph.getNode().add(ucc_firewall0);
            graph.getNode().add(ucc_router0);
            graph.getNode().add(ucc_switch);
            graph.getNode().add(DNP3_Master);
            graph.getNode().add(Scada_PI_server);
            graph.getNode().add(HMI);
            graph.getNode().add(ucc_router1);
            graph.getNode().add(ucc_firewall1);
            graph.getNode().add(ucc_pubdmz_webserver);
            graph.getNode().add(ucc_pubdmz_database);
            graph.getNode().add(ucc_dmz_router2);
            graph.getNode().add(ucc_dmz_firewall2);
            graph.getNode().add(ucc_switch_pubdmz);
            graph.getNode().add(ucc_vendor_dmz_webserver);
            graph.getNode().add(ucc_badmz_iccp_node);
            graph.getNode().add(ucc_corporate_dmz_webserver);
        }

        int index = 0;
        for (int i = 0; i < numberSS; ++i) {

            // Substations
            // [SS]: Firewall0
            String ip_ss_firewall0 = createRandomIP();
            Node ss_firewall0 = new Node();
            ss_firewall0.setName(ip_ss_firewall0);

            // Attach Firewall0 to UCC Firewall
            Neighbour ss_firewall0_neighbour = new Neighbour();
            ss_firewall0_neighbour.setName(ss_firewall0.getName());
            // Attach UCC Firewall to Firewall0
            ucc_firewall[index].getNeighbour().add(ss_firewall0_neighbour);

//             Neighbour ucc_switch_pubdmz_neighbour = new Neighbour();
//             ucc_switch_pubdmz_neighbour.setName(ucc_switch_pubdmz.getName());
//             ucc_firewall0.getNeighbour().add(ucc_switch_pubdmz_neighbour);

            // Attach UCC Firewall to Firewall0
            Neighbour ucc_firewall0_neighbour = new Neighbour();
            ucc_firewall0_neighbour.setName(ucc_firewall[index].getName());
            // Attach Firewall0 to UCC Firewall
            ss_firewall0.getNeighbour().add(ucc_firewall0_neighbour);
            // Increment index for next UCC
            index = (index + 1) % numberUCC;

            // [SS]: Router0
            String ip_ss_router0 = createRandomIP();
            Node ss_router0 = new Node();
            ss_router0.setFunctionalType(FunctionalTypes.FORWARDER);
            ss_router0.setName(ip_ss_router0);
            // Attach Router0 to Firewall0
            Neighbour ss_router0_neighbour = new Neighbour();
            ss_router0_neighbour.setName(ss_router0.getName());
            ss_firewall0.getNeighbour().add(ss_router0_neighbour);
            // Attach Firewall0 to Router0
            ss_router0.getNeighbour().add(ss_firewall0_neighbour);

            // [SS]: Firewall1
            String ip_ss_firewall1 = createRandomIP();
            Node ss_firewall1 = new Node();
            ss_firewall1.setName(ip_ss_firewall1);

            // Attach Firewall1 to Router0
            Neighbour ss_firewall1_neighbour = new Neighbour();
            ss_firewall1_neighbour.setName(ss_firewall1.getName());
            ss_router0.getNeighbour().add(ss_firewall1_neighbour);
            // Attach Router0 to Firewall1
            ss_firewall1.getNeighbour().add(ss_router0_neighbour);

            // [SS]: Switch0 - dnp3 usage
            String ip_ss_switch0 = createRandomIP();
            Node ss_switch0 = new Node();
            ss_switch0.setFunctionalType(FunctionalTypes.FORWARDER);
            ss_switch0.setName(ip_ss_switch0);
            // Attach Switch0 to Firewall1
            Neighbour ss_switch0_neighbour = new Neighbour();
            ss_switch0_neighbour.setName(ss_switch0.getName());
            ss_firewall1.getNeighbour().add(ss_switch0_neighbour);
            // Attach Firewall1 to Switch0
            ss_switch0.getNeighbour().add(ss_firewall1_neighbour);

            // [SS]: RTU0 (Remote Terminal Unit) - DNP3 O/S

            // L’RTU raccoglie dati dai dispositivi sul campo (Relay A, B, C) e li fornisce
            // al sistema centrale di supervisione (tipicamente SCADA).
            // Quindi risponde a richieste o invia dati periodicamente, proprio come fa un
            // server.
            String ip_ss_dnp3_os = createRandomIP();
            Node ss_dnp3_os = new Node();
            ss_dnp3_os.setFunctionalType(FunctionalTypes.WEBSERVER);
            ss_dnp3_os.setName(ip_ss_dnp3_os);
            Configuration confRTU0 = new Configuration();
            confRTU0.setName("confRTU0");
            confRTU0.setDescription("[SS DNP3 O/S_" + i + "]");
            Webserver ss_rtu_ws0 = new Webserver();
            ss_rtu_ws0.setName(ss_dnp3_os.getName());
            confRTU0.setWebserver(ss_rtu_ws0);
            ss_dnp3_os.setConfiguration(confRTU0);
            // Attach DNP3 O/S to Switch0
            Neighbour ss_dnp3_os_neighbour = new Neighbour();
            ss_dnp3_os_neighbour.setName(ss_dnp3_os.getName());
            ss_switch0.getNeighbour().add(ss_dnp3_os_neighbour);
            // Attach Switch0 to DNP3 O/S
            ss_dnp3_os.getNeighbour().add(ss_switch0_neighbour);
            // Add DNP3 O/S to the DNP3 Policy
            dnp3_os[i] = ip_ss_dnp3_os;

            // [SS]: Relay Controller0 - FORWARDER

            // Il Relay Controller riceve comandi (es. di apertura/chiusura relè) da un
            // sistema superiore (RTU o SCADA).
            // Risponde con informazioni di stato (es. "Relè chiuso", "Errore", ecc.).
            String ip_ss_relay_controller0 = createRandomIP();
            Node ss_relay_controller0 = new Node();
            ss_relay_controller0.setFunctionalType(FunctionalTypes.FORWARDER);
            ss_relay_controller0.setName(ip_ss_relay_controller0);
            // Attach Relay Controller0 to Switch0
            Neighbour ss_relay_controller0_neighbour = new Neighbour();
            ss_relay_controller0_neighbour.setName(ss_relay_controller0.getName());
            ss_switch0.getNeighbour().add(ss_relay_controller0_neighbour);
            // Attach Switch0 to Relay Controller0
            ss_relay_controller0.getNeighbour().add(ss_switch0_neighbour);

            // SERVER FITTIZIO PER GLI ENDPOINT RELAY
            String ip_dummy_server = createRandomIP();
            Node dummy_server = new Node();
            dummy_server.setFunctionalType(FunctionalTypes.WEBSERVER);
            dummy_server.setName(ip_dummy_server);
            Configuration confDummy = new Configuration();
            confDummy.setName("confDummy");
            confDummy.setDescription("[Dummy Server]");
            Webserver dummy_ws = new Webserver();
            dummy_ws.setName(dummy_server.getName());
            confDummy.setWebserver(dummy_ws);
            dummy_server.setConfiguration(confDummy);

            // [SS]: Relay A - ENDPOINT - CLIENT
            String ip_ss_relay_a = createRandomIP();
            Node ss_relay_a = new Node();
            ss_relay_a.setFunctionalType(FunctionalTypes.WEBCLIENT);
            ss_relay_a.setName(ip_ss_relay_a);
            // Attach Relay A to Relay Controller0
            Neighbour ss_relay_a_neighbour = new Neighbour();
            ss_relay_a_neighbour.setName(ss_relay_a.getName());
            ss_relay_controller0.getNeighbour().add(ss_relay_a_neighbour);
            // Attach Relay Controller0 to Relay A
            ss_relay_a.getNeighbour().add(ss_relay_controller0_neighbour);

            Configuration confRelayA = new Configuration();
            confRelayA.setName("confRelayA");
            confRelayA.setDescription("[SS Relay A_" + i + "]");
            Webclient ss_relay_a_client = new Webclient();
            // Server Fittizio
            ss_relay_a_client.setNameWebServer(dummy_server.getName());
            confRelayA.setWebclient(ss_relay_a_client);
            ss_relay_a.setConfiguration(confRelayA);

            // [SS]: Relay B - ENDPOINT - CLIENT
            String ip_ss_relay_b = createRandomIP();
            Node ss_relay_b = new Node();
            ss_relay_b.setFunctionalType(FunctionalTypes.WEBCLIENT);
            ss_relay_b.setName(ip_ss_relay_b);
            // Attach Relay B to Relay Controller0
            Neighbour ss_relay_b_neighbour = new Neighbour();
            ss_relay_b_neighbour.setName(ss_relay_b.getName());
            ss_relay_controller0.getNeighbour().add(ss_relay_b_neighbour);
            // Attach Relay Controller0 to Relay B
            ss_relay_b.getNeighbour().add(ss_relay_controller0_neighbour);

            Configuration confRelayB = new Configuration();
            confRelayB.setName("confRelayB");
            confRelayB.setDescription("[SS Relay B_" + i + "]");
            Webclient ss_relay_b_client = new Webclient();
            // Server Fittizio
            ss_relay_b_client.setNameWebServer(dummy_server.getName());
            confRelayB.setWebclient(ss_relay_b_client);
            ss_relay_b.setConfiguration(confRelayB);

            // [SS]: Relay C - ENDPOINT - CLIENT
            String ip_ss_relay_c = createRandomIP();
            Node ss_relay_c = new Node();
            ss_relay_c.setFunctionalType(FunctionalTypes.WEBCLIENT);
            ss_relay_c.setName(ip_ss_relay_c);
            // Attach Relay C to Relay Controller0
            Neighbour ss_relay_c_neighbour = new Neighbour();
            ss_relay_c_neighbour.setName(ss_relay_c.getName());
            ss_relay_controller0.getNeighbour().add(ss_relay_c_neighbour);
            // Attach Relay Controller0 to Relay C
            ss_relay_c.getNeighbour().add(ss_relay_controller0_neighbour);

            Configuration confRelayC = new Configuration();
            confRelayC.setName("confRelayC");
            confRelayC.setDescription("[SS Relay C_" + i + "]");
            Webclient ss_relay_c_client = new Webclient();
            // Server Fittizio
            ss_relay_c_client.setNameWebServer(dummy_server.getName());
            confRelayC.setWebclient(ss_relay_c_client);
            ss_relay_c.setConfiguration(confRelayC);

            // [SS]: Switch1 - endpoint usage
            String ip_ss_switch1 = createRandomIP();
            Node ss_switch1 = new Node();
            ss_switch1.setFunctionalType(FunctionalTypes.FORWARDER);
            ss_switch1.setName(ip_ss_switch1);
            // Attach Switch1 to Router0
            Neighbour ss_switch1_neighbour = new Neighbour();
            ss_switch1_neighbour.setName(ss_switch1.getName());
            ss_router0.getNeighbour().add(ss_switch1_neighbour);
            // Attach Router0 to Switch1
            ss_switch1.getNeighbour().add(ss_router0_neighbour);

            // [SS]: Client - endpoint rappresentation
            String ip_ss_client = createRandomIP();
            Node ss_client = new Node();
            ss_client.setFunctionalType(FunctionalTypes.WEBCLIENT);
            ss_client.setName(ip_ss_client);
            Configuration confClient = new Configuration();
            confClient.setName("confClient");
            confClient.setDescription("[SS Client_" + i + "]");
            Webclient ss_client_webclient = new Webclient();
            // Server Fittizio
            ss_client_webclient.setNameWebServer(dummy_server.getName());
            confClient.setWebclient(ss_client_webclient);
            ss_client.setConfiguration(confClient);
            // Attach Client to Switch1
            Neighbour ss_client_neighbour = new Neighbour();
            ss_client_neighbour.setName(ss_client.getName());
            ss_switch1.getNeighbour().add(ss_client_neighbour);
            // Attach Switch1 to Client
            ss_client.getNeighbour().add(ss_switch1_neighbour);

            // [SS]: Switch2 - Substation DMZ
            String ip_ss_subdmz_switch2 = createRandomIP();
            Node ss_subdmz_switch2 = new Node();
            ss_subdmz_switch2.setFunctionalType(FunctionalTypes.FORWARDER);
            ss_subdmz_switch2.setName(ip_ss_subdmz_switch2);
            // Attach Substation DMZ Switch to Router0
            Neighbour ss_subdmz_switch2_neighbour = new Neighbour();
            ss_subdmz_switch2_neighbour.setName(ss_subdmz_switch2.getName());
            ss_router0.getNeighbour().add(ss_subdmz_switch2_neighbour);
            // Attach Router0 to Substation DMZ Switch
            ss_subdmz_switch2.getNeighbour().add(ss_router0_neighbour);

            // [SS]: Substation DMZ Webserver
            String ip_ss_subdmz_webserver = createRandomIP();
            Node ss_subdmz_webserver = new Node();
            ss_subdmz_webserver.setFunctionalType(FunctionalTypes.WEBSERVER);
            ss_subdmz_webserver.setName(ip_ss_subdmz_webserver);
            Configuration confSubDMZ = new Configuration();
            confSubDMZ.setName("confSubDMZ");
            confSubDMZ.setDescription("[SS Substation DMZ Webserver_" + i + "]");
            Webserver ss_subdmz_ws = new Webserver();
            ss_subdmz_ws.setName(ss_subdmz_webserver.getName());
            confSubDMZ.setWebserver(ss_subdmz_ws);
            ss_subdmz_webserver.setConfiguration(confSubDMZ);
            // Attach Substation DMZ Webserver to Substation DMZ Switch
            Neighbour ss_subdmz_webserver_neighbour = new Neighbour();
            ss_subdmz_webserver_neighbour.setName(ss_subdmz_webserver.getName());
            ss_subdmz_switch2.getNeighbour().add(ss_subdmz_webserver_neighbour);
            // Attach Substation DMZ Switch to Substation DMZ Webserver
            ss_subdmz_webserver.getNeighbour().add(ss_subdmz_switch2_neighbour);

            // [SS]: Substation DMZ Database
            String ip_ss_subdmz_local_data_historian = createRandomIP();
            Node ss_subdmz_local_data_historian = new Node();
            ss_subdmz_local_data_historian.setFunctionalType(FunctionalTypes.WEBSERVER);
            ss_subdmz_local_data_historian.setName(ip_ss_subdmz_local_data_historian);
            Configuration confSubDB = new Configuration();
            confSubDB.setName("confSubDB");
            confSubDB.setDescription("[SS Substation DMZ Database_" + i + "]");
            Webserver ss_subdmz_db = new Webserver();
            ss_subdmz_db.setName(ss_subdmz_local_data_historian.getName());
            confSubDB.setWebserver(ss_subdmz_db);
            ss_subdmz_local_data_historian.setConfiguration(confSubDB);
            // Attach Substation DMZ Database to Substation DMZ Switch
            Neighbour ss_subdmz_db_neighbour = new Neighbour();
            ss_subdmz_db_neighbour.setName(ss_subdmz_local_data_historian.getName());
            ss_subdmz_switch2.getNeighbour().add(ss_subdmz_db_neighbour);
            // Attach Substation DMZ Switch to Substation DMZ Database
            ss_subdmz_local_data_historian.getNeighbour().add(ss_subdmz_switch2_neighbour);
            // Add Substation DMZ Local Data Historian to Policy
            subdmz_local_data_historian[i] = ip_ss_subdmz_local_data_historian;

            // Add to graph
            graph.getNode().add(ss_firewall0);
            graph.getNode().add(ss_router0);
            graph.getNode().add(ss_firewall1);
            graph.getNode().add(ss_switch0);
            graph.getNode().add(ss_dnp3_os);
            graph.getNode().add(ss_relay_controller0);
            graph.getNode().add(ss_relay_a);
            graph.getNode().add(ss_relay_b);
            graph.getNode().add(ss_relay_c);
            graph.getNode().add(ss_switch1);
            graph.getNode().add(ss_client);
            graph.getNode().add(ss_subdmz_switch2);
            graph.getNode().add(ss_subdmz_webserver);
            graph.getNode().add(ss_subdmz_local_data_historian);
        }

        //TODO: METTERE ENDPOINT A POSTO DI WEBSERER RANDOM

        // Policy
        for(int i = 0; i < numberSS; ++i) {
            // DNP3
            createPolicyPair(nfv, graph, dnp3_master[i%numberUCC], dnp3_os[i], L4ProtocolTypes.ANY, "102", withPorts);
        }

        for(int i = 0; i < numberUCC; ++i) {
            // HTTPS: Vendor - Public DMZ Webserver
            createPolicyPair(nfv, graph, vendor[i], pubdmz_webserver[i], L4ProtocolTypes.ANY, "443", withPorts);
            // HTTPS: Utility Partner - Public DMZ Webserver
            createPolicyPair(nfv, graph, corporate_dmz_utility[i], pubdmz_webserver[i], L4ProtocolTypes.ANY, "443", withPorts);

            // SQL - Microsoft SQL Server: Vendor - Public DMZ Database
            createPolicyPair(nfv, graph, vendor[i], pubdmz_database[i], L4ProtocolTypes.ANY, "1433", withPorts);

            // SQL - Microsoft SQL Server: BA ICCP Node - Public DMZ Database
            createPolicyPair(nfv, graph, ba_iccp_node[i], pubdmz_database[i], L4ProtocolTypes.ANY, "1433", withPorts);
            // SQL - Microsoft SQL Server: Utility Partner - Public DMZ Database
            createPolicyPair(nfv, graph, corporate_dmz_utility[i], pubdmz_database[i], L4ProtocolTypes.ANY, "1433", withPorts);

            // ICCP ICCP Server - BA ICCP Node
            createPolicyPair(nfv, graph, ip_iccp_server, ba_iccp_node[i], L4ProtocolTypes.ANY, "102", withPorts);
        }

        for(int i = 0; i < numberSS; ++i) {
            // SQL - Microsoft SQL Server: DNP3 O/S - Substation DMZ Local Data Historian
            createPolicyPair(nfv, graph, dnp3_os[i], subdmz_local_data_historian[i], L4ProtocolTypes.ANY, "433", withPorts);
        }

        // Add graph to graphs
        nfv.getGraphs().getGraph().add(graph);

        // Add isolation properties if requested
        if (withIsolation) {
            createIsolationPolicies(nfv, graph, nfv.getPropertyDefinition().getProperty().size());
        }

        return nfv;
    }

    /**
     * Converts NFV to XML and prints it
     */
    public void printNFVAsXML(NFV nfv) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(NFV.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            StringWriter sw = new StringWriter();
            marshaller.marshal(nfv, sw);

            System.out.println(sw.toString());

        } catch (JAXBException e) {
            System.err.println("Errore durante la serializzazione XML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    /**
     * Main method for testing NFV generation
     */
    public static void main(String[] args) {
        TestCaseGeneratorTexas2000 generator = new TestCaseGeneratorTexas2000(1818498876, 1, 2, false, true, 5);
        NFV nfv = generator.generateNFV(3, 3, true, true, 30);
        generator.printNFVAsXML(nfv);
    }
}
