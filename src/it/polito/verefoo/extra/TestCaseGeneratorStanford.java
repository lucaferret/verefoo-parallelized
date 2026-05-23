package it.polito.verefoo.extra;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import it.polito.verefoo.jaxb.*;

// Auxiliary class to generate  test cases for performance tests (used by TestPerformanceScalability)
public class TestCaseGeneratorStanford {
    NFV nfv;
    String name;
    Random rand = null;

    String IPC;
    String IPAP;
    String IPS;

    Map<String, Node> rtrNameToNode;
    Set<String> allIPs;
    List<Node> allAPS;

    public class ACLRule {
        String routerName;
        String action;
        String protocol;
        String source;
        String destination;
        String extra;
    }

    public TestCaseGeneratorStanford(String name, int numberPolicies, int seed, double reachabilityPercent, double completeReachabilityPercent, double portSpecificPercent) {
        this.name = name;
        this.rand = new Random(seed);

        allAPS = new ArrayList<Node>();
        rtrNameToNode = new java.util.HashMap<String, Node>();
        allIPs = new HashSet<String>();
        nfv = generateNFV(numberPolicies, rand, reachabilityPercent, completeReachabilityPercent, portSpecificPercent);
    }

    private String createIP() {
        int first, second, third, forth;
        first = rand.nextInt(256);
        if(first == 0) first++;
        second = rand.nextInt(256);
        third = rand.nextInt(256);
        forth = rand.nextInt(256);

        return first + "." + second + "." + third + "." + forth;
    }


    private String createRandomIP() {
        boolean notCreated = true;
        String ip = null;
        while(notCreated) {
            ip = createIP();
            if(!allIPs.contains(ip)) {
                notCreated = false;
                allIPs.add(ip);
            }
        }
        return ip;
    }

    private void connect(Node a, Node b) {
        Neighbour n1 = new Neighbour();
        n1.setName(b.getName());
        a.getNeighbour().add(n1);

        Neighbour n2 = new Neighbour();
        n2.setName(a.getName());
        b.getNeighbour().add(n2);
    }


    public NFV generateNFV(int numberPolicies, Random rand, double reachabilityPercent, double completeReachabilityPercent, double portSpecificPercent) {


        /* Creation of the test */

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
        graph.setId((long) 0);

        //creation of the backbone nodes -> MANUAL configuration for each router and its links
        Node bbra = new Node();
        Configuration confBbra = new Configuration();
        confBbra.setName("bbra");
        bbra.setName("10.0.0.1");
        //bbra.setConfiguration(confBbra);
        rtrNameToNode.put("bbra", bbra);
        allAPS.add(bbra);

        // bbrb
        Node bbrb = new Node();
        Configuration confBbrb = new Configuration();
        confBbrb.setName("bbrb");
        bbrb.setName("10.0.0.2");
        //bbrb.setConfiguration(confBbrb);
        rtrNameToNode.put("bbrb", bbrb);
        allAPS.add(bbrb);

        String ip = new String();
        // coza
        ip = "10.0.0.3";
        Node coza = new Node();
        Configuration confCoza = new Configuration();
        confCoza.setName("coza");
        coza.setName(ip);
        //coza.setConfiguration(confCoza);
        rtrNameToNode.put("coza", coza);
        allAPS.add(coza);

        // cozb
        ip = "10.0.0.4";
        Node cozb = new Node();
        Configuration confCozb = new Configuration();
        confCozb.setName("cozb");
        cozb.setName(ip);
        //cozb.setConfiguration(confCozb);
        rtrNameToNode.put("cozb", cozb);
        allAPS.add(cozb);

        // soza
        ip = "10.0.0.5";
        Node soza = new Node();
        Configuration confSoza = new Configuration();
        confSoza.setName("soza");
        soza.setName(ip);
        //soza.setConfiguration(confSoza);
        rtrNameToNode.put("soza", soza);
        allAPS.add(soza);

        // sozb
        ip = "10.0.0.6";
        Node sozb = new Node();
        Configuration confSozb = new Configuration();
        confSozb.setName("sozb");
        sozb.setName(ip);
        //sozb.setConfiguration(confSozb);
        rtrNameToNode.put("sozb", sozb);
        allAPS.add(sozb);

        for(int i = 0; i < allAPS.size(); i++) {
            Node n1 = allAPS.get(i);
            for(int j = i+1; j < allAPS.size(); j++) {
                if(i != j) {
                    Node n2 = allAPS.get(j);
                    connect(n1, n2);
                }
            }
        }

        // REMAINING PAIRS BELOW
        // yoza
        ip = "10.0.0.7";
        Node yoza = new Node();
        Configuration confYoza = new Configuration();
        confYoza.setName("yoza");
        yoza.setName(ip);
        //yoza.setConfiguration(confYoza);
        rtrNameToNode.put("yoza", yoza);
        allAPS.add(yoza);

        // yozb
        ip = "10.0.0.8";
        Node yozb = new Node();
        Configuration confYozb = new Configuration();
        confYozb.setName("yozb");
        yozb.setName(ip);
        //yozb.setConfiguration(confYozb);
        rtrNameToNode.put("yozb", yozb);
        allAPS.add(yozb);
        connect(yoza, yozb);

        // poza
        ip = "10.0.0.9";
        Node poza = new Node();
        Configuration confPoza = new Configuration();
        confPoza.setName("poza");
        poza.setName(ip);
        //poza.setConfiguration(confPoza);
        rtrNameToNode.put("poza", poza);
        allAPS.add(poza);

        // pozb
        ip = "10.0.0.10";
        Node pozb = new Node();
        Configuration confPozb = new Configuration();
        confPozb.setName("pozb");
        pozb.setName(ip);
        //pozb.setConfiguration(confPozb);
        rtrNameToNode.put("pozb", pozb);
        allAPS.add(pozb);
        connect(poza, pozb);

        // goza
        ip = "10.0.0.11";
        Node goza = new Node();
        Configuration confGoza = new Configuration();
        confGoza.setName("goza");
        goza.setName(ip);
        //goza.setConfiguration(confGoza);
        rtrNameToNode.put("goza", goza);
        allAPS.add(goza);

        // gozb
        ip = "10.0.0.12";
        Node gozb = new Node();
        Configuration confGozb = new Configuration();
        confGozb.setName("gozb");
        gozb.setName(ip);
        //gozb.setConfiguration(confGozb);
        rtrNameToNode.put("gozb", gozb);
        allAPS.add(gozb);
        connect(goza, gozb);

        // roza
        ip = "10.0.0.13";
        Node roza = new Node();
        Configuration confRoza = new Configuration();
        confRoza.setName("roza");
        roza.setName(ip);
        //roza.setConfiguration(confRoza);
        rtrNameToNode.put("roza", roza);
        allAPS.add(roza);

        // rozb
        ip = "10.0.0.14";
        Node rozb = new Node();
        Configuration confRozb = new Configuration();
        confRozb.setName("rozb");
        rozb.setName(ip);
        //rozb.setConfiguration(confRozb);
        rtrNameToNode.put("rozb", rozb);
        allAPS.add(rozb);
        connect(roza, rozb);

        // boza
        ip = "10.0.0.15";
        Node boza = new Node();
        Configuration confBoza = new Configuration();
        confBoza.setName("boza");
        boza.setName(ip);
        //boza.setConfiguration(confBoza);
        rtrNameToNode.put("boza", boza);
        allAPS.add(boza);

        // bozb
        ip = "10.0.0.16";
        Node bozb = new Node();
        Configuration confBozb = new Configuration();
        confBozb.setName("bozb");
        bozb.setName(ip);
        //bozb.setConfiguration(confBozb);
        rtrNameToNode.put("bozb", bozb);
        allAPS.add(bozb);
        connect(boza, bozb);


        graph.getNode().addAll(allAPS);
        nfv.getGraphs().getGraph().add(graph);

        /* Invoke the automatic CSV loading of the ACL */
        try{
            List<Property> aclProperties = loadACLsFromCSV(
                    graph,
                    "./resources/router_acls_all.csv",
                    numberPolicies,     // tot acls to load
                    reachabilityPercent,    // e.g. 20% reachability, 80% isolation -> 0.2
                    completeReachabilityPercent,  // e.g. 25% of the reach. properties, must be COMPLETE REACHABILITY -> 0.25
                    portSpecificPercent  // 20% with a specific port number or range -> 0.2
            );

            for (Property p : aclProperties) {
                nfv.getPropertyDefinition().getProperty().add(p);
            }

        } catch (Exception e) {
            System.out.println("Error reading ACLs from CSV: " + e.getMessage());
            e.printStackTrace();
        }

        return nfv;
    }


    public List<Property> loadACLsFromCSV(Graph graph, String csvPath, int totalACLsToLoad, double reachPercentage, double completeReachPercentage, double portSpecificPercentage) throws IOException {

        List<ACLRule> allACLs = new ArrayList<>();

        // 1. load csv
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean headerSkipped = false;
            while ((line = br.readLine()) != null) {
                if (!headerSkipped) { headerSkipped = true; continue; }

                String[] tokens = line.split(",");
                if (tokens.length < 6) continue;

                ACLRule acl = new ACLRule();
                acl.routerName = tokens[0].trim();
                acl.action = tokens[2].trim().toUpperCase();
                acl.protocol = tokens[3].trim();
                acl.source = tokens[4].trim();
                acl.destination = tokens[5].trim();
                acl.extra = tokens.length >= 7 ? tokens[6].trim() : null; // check length safely

                allACLs.add(acl);
            }
        }

        // 2. partition into 4 buckets

        //helper function to check if ACL has port specification
        java.util.function.Predicate<ACLRule> hasPort = a -> {
            if (a.extra == null || a.extra.isEmpty()) return false;
            if (a.extra.startsWith("eq")) {
                String[] parts = a.extra.split("\\s+");
                return parts.length > 1 && parts[1].matches("\\d+");
            } else if (a.extra.startsWith("range")) {
                String[] parts = a.extra.split("\\s+");
                return parts.length > 2 && parts[1].matches("\\d+") && parts[2].matches("\\d+");
            }
            return false;
        };

        List<ACLRule> allowWithPort = new ArrayList<>();
        List<ACLRule> allowNoPort = new ArrayList<>();
        List<ACLRule> denyWithPort = new ArrayList<>();
        List<ACLRule> denyNoPort = new ArrayList<>();

        for (ACLRule acl : allACLs) {
            boolean isAllow = acl.action.equals("PERMIT");
            boolean isPort = hasPort.test(acl);

            if (isAllow && isPort) allowWithPort.add(acl);
            else if (isAllow && !isPort) allowNoPort.add(acl);
            else if (!isAllow && isPort) denyWithPort.add(acl);
            else if (!isAllow && !isPort) denyNoPort.add(acl);
        }

        Random rand = new Random();
        Collections.shuffle(allowWithPort, rand);
        Collections.shuffle(allowNoPort, rand);
        Collections.shuffle(denyWithPort, rand);
        Collections.shuffle(denyNoPort, rand);

        // 3. compute quantities to load from each bucket

        // A. check total
        int availableTotal = allACLs.size();
        if (totalACLsToLoad > availableTotal) {
            System.out.println("WARNING: Requested " + totalACLsToLoad + " but only found " + availableTotal);
            totalACLsToLoad = availableTotal;
        }

        int targetAllow = (int) Math.round(totalACLsToLoad * reachPercentage);
        int targetDeny = totalACLsToLoad - targetAllow;

        // Check Allow/Deny
        int availableAllow = allowWithPort.size() + allowNoPort.size();
        int availableDeny = denyWithPort.size() + denyNoPort.size();

        // NOT ENOUGH ACLs from one of the two major buckets -> cap
        if (targetAllow > availableAllow) {
            System.out.println("WARNING: Not enough Allow ACLs. Capping Allow.");
            targetAllow = availableAllow;
        } else if (targetDeny > availableDeny) {
            System.out.println("WARNING: Not enough Deny ACLs. Capping Deny.");
            targetDeny = availableDeny;
        }

        int targetTotalPort = (int) Math.round(totalACLsToLoad * portSpecificPercentage);

        // balance using reachPercentage allow with port vs deny with port
        int targetAllowPort = (int) Math.round(targetTotalPort * reachPercentage);
        int targetDenyPort = targetTotalPort - targetAllowPort;

        // D. Balancing with deny if AllowWithPort is insufficient

        int finalAllowPort = 0;
        int deficitAllowPort = 0;

        if (targetAllowPort <= allowWithPort.size()) {
            finalAllowPort = targetAllowPort;
        } else {
            // not enopugh AllowWithPort
            finalAllowPort = allowWithPort.size();
            deficitAllowPort = targetAllowPort - finalAllowPort; // try to balance taking these from DenyWithPort
        }

        int finalDenyPort = 0;
        int requestedDenyPort = targetDenyPort + deficitAllowPort;

        if (requestedDenyPort <= denyWithPort.size()) {
            finalDenyPort = requestedDenyPort;
        } else {
            // definetly not enough
            finalDenyPort = denyWithPort.size();
            System.out.println("WARNING: Cannot satisfy portSpecificPercentage even mixing Allow/Deny.");
        }

        // E. Fill the rest with NoPort

        int finalAllowNoPort = targetAllow - finalAllowPort;
        int finalDenyNoPort = targetDeny - finalDenyPort;

        List<ACLRule> selectedAllowNoPort = new ArrayList<>();
        if (finalAllowNoPort <= allowNoPort.size() && finalAllowNoPort >= 0) {
            selectedAllowNoPort.addAll(allowNoPort.subList(0, finalAllowNoPort));
        } else {
            selectedAllowNoPort.addAll(allowNoPort);
            int missing = finalAllowNoPort - allowNoPort.size();
            int remainingWithPort = allowWithPort.size() - finalAllowPort;
            int toTake = Math.min(missing, remainingWithPort);
            if (toTake > 0) {
                selectedAllowNoPort.addAll(allowWithPort.subList(finalAllowPort, finalAllowPort + toTake));
            }
        }

        List<ACLRule> selectedDenyNoPort = new ArrayList<>();
        if (finalDenyNoPort <= denyNoPort.size() && finalDenyNoPort >= 0) {
            selectedDenyNoPort.addAll(denyNoPort.subList(0, finalDenyNoPort));
        } else {
            selectedDenyNoPort.addAll(denyNoPort);
            int missing = finalDenyNoPort - denyNoPort.size();
            int remainingWithPort = denyWithPort.size() - finalDenyPort;
            int toTake = Math.min(missing, remainingWithPort);
            if (toTake > 0) {
                selectedDenyNoPort.addAll(denyWithPort.subList(finalDenyPort, finalDenyPort + toTake));
            }
        }

        List<Property> properties = new ArrayList<>();
        int completeReachCounter = 0;
        int completeReachTarget = (int) Math.round(targetAllow * completeReachPercentage);

        for (int i = 0; i < finalAllowPort; i++) {
            boolean isComplete = completeReachCounter < completeReachTarget;
            if(isComplete) completeReachCounter++;
            properties.add(createProperty(graph, allowWithPort.get(i), true, isComplete));
        }


        for (ACLRule acl : selectedAllowNoPort) {
            boolean isComplete = completeReachCounter < completeReachTarget;
            if(isComplete) completeReachCounter++;
            properties.add(createProperty(graph, acl, false, isComplete)); // Force no port extraction
        }


        for (int i = 0; i < finalDenyPort; i++) {
            properties.add(createProperty(graph, denyWithPort.get(i), true, false));
        }


        for (ACLRule acl : selectedDenyNoPort) {
            properties.add(createProperty(graph, acl, false, false));
        }


        Collections.shuffle(properties, rand);

        //System.out.println("DEBUG: Generated " + properties.size() + " policies.");
        return properties;
    }


    private Property createProperty(Graph graph, ACLRule acl, boolean extractPort, boolean isCompleteReachability) {
        Property p = new Property();
        p.setGraph(0L);

        if (acl.action.equals("DENY")) {
            p.setName(PName.ISOLATION_PROPERTY);
        } else {
            p.setName(isCompleteReachability ? PName.COMPLETE_REACHABILITY_PROPERTY : PName.REACHABILITY_PROPERTY);
        }

        if (extractPort && acl.extra != null) {
            if (acl.extra.trim().startsWith("eq")) {
                String[] parts = acl.extra.split("\\s+");
                if (parts.length >= 2 && parts[1].matches("\\d+")) {
                    p.setSrcPort(parts[1]);
                    p.setDstPort(parts[1]);
                }
            } else if(acl.extra.trim().startsWith("range")) {
                String[] parts = acl.extra.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        int start = Integer.parseInt(parts[1]);
                        int end = Integer.parseInt(parts[2]);
                        int port = (start + end) / 2;
                        p.setSrcPort(String.valueOf(port));
                        p.setDstPort(String.valueOf(port));
                    } catch (NumberFormatException e) { /* ignore */ }
                }
            }

            // Protocol logic
            if(acl.protocol != null){
                if(acl.protocol.equalsIgnoreCase("TCP")) p.setLv4Proto(L4ProtocolTypes.TCP);
                else if(acl.protocol.equalsIgnoreCase("UDP")) p.setLv4Proto(L4ProtocolTypes.UDP);
                else p.setLv4Proto(L4ProtocolTypes.OTHER);
            }
        }

        // Set Source/Dest
        Node src, dst;
        if (acl.source == null || acl.source.trim().equalsIgnoreCase("any")){
            src = createGetNode(graph, acl.routerName, createRandomIP(), true);
            //p.setSrc(tmp.getName());
        } else {
            src = createGetNode(graph, acl.routerName, acl.source, true);
            //p.setSrc(source.getName());
        }

        if (acl.destination == null || acl.destination.trim().equalsIgnoreCase("any")) {
            dst = createGetNode(graph, acl.routerName, createRandomIP(), false);
            //p.setDst(tmp.getName());
        } else {
            dst = createGetNode(graph, acl.routerName, acl.destination, false);
            //p.setDst(destination.getName());
        }
        Node routerNode = rtrNameToNode.get(acl.routerName);

        if(pathExists(src, dst, graph)) {
            p.setSrc(src.getName());
            p.setDst(dst.getName());
        } else{
            if(!pathExists(src, routerNode, graph)) {
                //create a brand new src node connected to the router
                Node newSrc = createNewNode(graph, acl.routerName, createRandomIP(), true);
                p.setSrc(newSrc.getName());
                p.setDst(dst.getName());

            }else{
                Node newDst = createNewNode(graph, acl.routerName, createRandomIP(), false);
                p.setDst(newDst.getName());
                p.setSrc(src.getName());
            }
        }

        return p;
    }

    private boolean pathExists(Node src, Node dst, Graph graph) {
        if (src == null || dst == null || graph == null) return false;
        if (src == dst) return true; // stesso nodo

        Set<String> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(src);
        visited.add(src.getName());

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.getNeighbour() != null) {
                for (Neighbour neigh : current.getNeighbour()) {
                    String neighName = neigh.getName();
                    if (!visited.contains(neighName)) {
                        visited.add(neighName);
                        Node neighNode = findNodeByName(graph, neighName);
                        if (neighNode != null) {
                            if (neighNode == dst) return true;
                            queue.add(neighNode);
                        }
                    }
                }
            }
        }
        return false; // Placeholder for actual path existence logic
    }

    private Node findNodeByName(Graph graph, String name) {
        if (graph.getNode() != null) {
            for (Node n : graph.getNode()) {
                if (name.equals(n.getName())) return n;
            }
        }
        return null;
    }

    private Node createGetNode(Graph graph, String routerName, String rawAddress, boolean isSource){

        String normalizedIP = normalizeACLAddress(rawAddress);

        for (Node n : graph.getNode()) {
            if (n.getName().equals(normalizedIP)) {
                return n; // node already existing, return it
            }
        }

        Node newNode = new Node();
        newNode.setName(normalizedIP);
        newNode.setFunctionalType(isSource ? FunctionalTypes.WEBCLIENT : FunctionalTypes.WEBSERVER);
        graph.getNode().add(newNode); // node didn't exist in graph -> insert it and connect with the correct backbone or OZ router
        allIPs.add(normalizedIP);
        for(String rtrName : rtrNameToNode.keySet()) {
            if(rtrName.equals(routerName)) {
                Node n = rtrNameToNode.get(rtrName);
                connect(newNode, n);
            }
        }

        return newNode;
    }

    private Node createNewNode(Graph graph, String routerName, String rawAddress, boolean isSource){
        String normalizedIP = normalizeACLAddress(rawAddress);
        Node newNode = new Node();
        newNode.setName(normalizedIP);
        newNode.setFunctionalType(isSource ? FunctionalTypes.WEBCLIENT : FunctionalTypes.WEBSERVER);
        graph.getNode().add(newNode); // node didn't exist in graph -> insert it and connect with the correct backbone or OZ router
        allIPs.add(normalizedIP);
        for(String rtrName : rtrNameToNode.keySet()) {
            if(rtrName.equals(routerName)) {
                Node n = rtrNameToNode.get(rtrName);
                connect(newNode, n);
            }
        }

        return newNode;
    }

    private String normalizeACLAddress(String ipField){
        String[] parts;
        if(ipField.contains("/")){
            parts = ipField.trim().split("/");
        } else{
            parts = ipField.trim().split("\\s+");
        }
        if (parts.length == 1) {
            return parts[0];
        }
        if (parts.length == 2) {
            String ipAddr = parts[0];
            String netmask = parts[1];

            String[] baseOctets = ipAddr.split("\\.");
            String[] maskOctets = netmask.split("\\.");

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < 4; i++) {
                if (maskOctets[i].equals("255")) {
                    sb.append(new Random().nextInt(256));
                    //sb.append("-1");
                } else {
                    sb.append(baseOctets[i]);
                }
                if (i < 3) sb.append(".");
            }
            return sb.toString();
        }

        return ipField; //return original means errors were encoutered during normalization
    }

    public NFV getNfv() {
        return nfv;
    }

    public void setNfv(NFV nfv) {
        this.nfv = nfv;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIPC() {
        return IPC;
    }

    public void setIPC(String iPC) {
        IPC = iPC;
    }

    public String getIPAP() {
        return IPAP;
    }

    public void setIPAP(String iPAP) {
        IPAP = iPAP;
    }

    public String getIPS() {
        return IPS;
    }

    public void setIPS(String iPS) {
        IPS = iPS;
    }


}
