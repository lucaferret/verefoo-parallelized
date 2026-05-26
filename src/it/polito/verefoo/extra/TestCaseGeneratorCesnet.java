package it.polito.verefoo.extra;

import it.polito.verefoo.jaxb.*;
import it.polito.verefoo.jaxb.AllocationConstraints.AllocationConstraint;

import java.util.*;

public class TestCaseGeneratorCesnet {
    NFV nfv = new NFV();
    Map<String, Node> nodesMap = new HashMap<>();
    Graphs graphs = new Graphs();
    Graph graph = new Graph();
    PropertyDefinition pd = new PropertyDefinition();
    Constraints cnst = new Constraints();
    NodeConstraints nc = new NodeConstraints();
    LinkConstraints lc = new LinkConstraints();

    Boolean isolationBidirectional = false;
    Boolean usePorts = false;
    int policyNumber = 0;
    String fileName;
    Double reachabilityPerc = 0.0;

    int mainNodes = 0;
    int others = 0;
    int prahaPercent = 0;
    int ustiPercent = 0;
    int ostravaPercent = 0;
    int brnoPercent = 0;


    public TestCaseGeneratorCesnet(String fileName, Boolean isolationBidirectional, int policyNumber, Double reachabilityPerc, Boolean usePorts) {
        super();
        this.fileName = fileName;
        this.isolationBidirectional = isolationBidirectional;
        this.policyNumber = policyNumber;
        this.reachabilityPerc = reachabilityPerc;
        this.usePorts = usePorts;


        cnst.setNodeConstraints(nc);
        cnst.setLinkConstraints(lc);
        nfv.setGraphs(graphs);
        nfv.setPropertyDefinition(pd);
        nfv.setConstraints(cnst);
        Graph graph = new Graph();
        graph.setId((long) 0);
        graph.setServiceGraph(true);

        // Build the CESNET network
        cesnetTopology(graph);

        // Select the nodes of the network to use for the policy creation
        Map<Node, List<Node>> selectedNodes = selectRandomNodes(graph, policyNumber, cnst);
        graphs.getGraph().add(graph);
        nfv.setGraphs(graphs);

        // Create the policy
        createPolicy(selectedNodes, policyNumber, isolationBidirectional, reachabilityPerc, usePorts);
    }

    /*
     * Creates a bidirectional connection between two nodes by adding each one to the other's list of neighbours
     */
    public static void connect(Node a, Node b) {
        Neighbour na = new Neighbour();
        na.setName(b.getName());
        a.getNeighbour().add(na);

        Neighbour nb = new Neighbour();
        nb.setName(a.getName());
        b.getNeighbour().add(nb);
    }

    /*
     * Break the connection between two nodes
     */
    public static void disconnect(Node a, Node b) {
        a.getNeighbour().removeIf(n -> n.getName().equals(b.getName()));
        b.getNeighbour().removeIf(n -> n.getName().equals(a.getName()));
    }

    /*
     * Create the CESNET network by adding first the nodes, which can be WEBCLIENT or FORWARDER,
     * and then connect them depending on the real topology of the network
     */
    public void cesnetTopology(Graph graph) {
        // NODE INITIALIZATION

        // Node: PRAHA
        Node praha = new Node();
        praha.setName("146.102.200.120");
        Configuration confPraha = new Configuration();
        confPraha.setName("confA");
        confPraha.setDescription("Praha");
        praha.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwPraha = new Forwarder();
        forwPraha.setName(confPraha.getDescription());
        confPraha.setForwarder(forwPraha);
        praha.setConfiguration(confPraha);
        nodesMap.put(praha.getConfiguration().getDescription(), praha);
        graph.getNode().add(praha);

        Node brno = new Node();
        brno.setName("147.251.100.25");
        Configuration confBrno = new Configuration();
        confBrno.setName("confA");
        confBrno.setDescription("Brno");
        brno.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwBrno = new Forwarder();
        forwBrno.setName(confBrno.getDescription());
        confBrno.setForwarder(forwBrno);
        brno.setConfiguration(confBrno);
        nodesMap.put(brno.getConfiguration().getDescription(), brno);
        graph.getNode().add(brno);

        Node pardubice = new Node();
        pardubice.setName("195.113.165.33");
        Configuration confPardubice = new Configuration();
        confPardubice.setName("confA");
        confPardubice.setDescription("Pardubice");
        pardubice.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwPardubice = new Forwarder();
        forwPardubice.setName(confPardubice.getDescription());
        confPardubice.setForwarder(forwPardubice);
        pardubice.setConfiguration(confPardubice);
        nodesMap.put(pardubice.getConfiguration().getDescription(), pardubice);
        graph.getNode().add(pardubice);

        Node ustiNL = new Node();
        ustiNL.setName("195.113.198.50");
        Configuration confUstiNL = new Configuration();
        confUstiNL.setName("confA");
        confUstiNL.setDescription("Usti nad Labem");
        ustiNL.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwUstiNL = new Forwarder();
        forwUstiNL.setName(confUstiNL.getDescription());
        confUstiNL.setForwarder(forwUstiNL);
        ustiNL.setConfiguration(confUstiNL);
        nodesMap.put(ustiNL.getConfiguration().getDescription(), ustiNL);
        graph.getNode().add(ustiNL);

        Node plzen = new Node();
        plzen.setName("147.228.2.7");
        Configuration confPlzen = new Configuration();
        confPlzen.setName("confA");
        confPlzen.setDescription("Plzen");
        plzen.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwPlzen = new Forwarder();
        forwPlzen.setName(confPlzen.getDescription());
        confPlzen.setForwarder(forwPlzen);
        plzen.setConfiguration(confPlzen);
        nodesMap.put(plzen.getConfiguration().getDescription(), plzen);
        graph.getNode().add(plzen);

        Node hradecKralove = new Node();
        hradecKralove.setName("195.113.106.122");
        Configuration confHradec = new Configuration();
        confHradec.setName("confA");
        confHradec.setDescription("Hradec Kralove");
        hradecKralove.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwHradec = new Forwarder();
        forwHradec.setName(confHradec.getDescription());
        confHradec.setForwarder(forwHradec);
        hradecKralove.setConfiguration(confHradec);
        nodesMap.put(hradecKralove.getConfiguration().getDescription(), hradecKralove);
        graph.getNode().add(hradecKralove);

        Node liberec = new Node();
        liberec.setName("147.230.17.200");
        Configuration confLiberec = new Configuration();
        confLiberec.setName("confA");
        confLiberec.setDescription("Liberec");
        liberec.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwLiberec = new Forwarder();
        forwLiberec.setName(confLiberec.getDescription());
        confLiberec.setForwarder(forwLiberec);
        liberec.setConfiguration(confLiberec);
        nodesMap.put(liberec.getConfiguration().getDescription(), liberec);
        graph.getNode().add(liberec);

        Node ceskeBud = new Node();
        ceskeBud.setName("195.113.145.101");
        Configuration confCeskeBud = new Configuration();
        confCeskeBud.setName("confA");
        confCeskeBud.setDescription("Ceske Budejovice");
        ceskeBud.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwCeskeBud = new Forwarder();
        forwCeskeBud.setName(confCeskeBud.getDescription());
        confCeskeBud.setForwarder(forwCeskeBud);
        ceskeBud.setConfiguration(confCeskeBud);
        nodesMap.put(ceskeBud.getConfiguration().getDescription(), ceskeBud);
        graph.getNode().add(ceskeBud);

        Node budkov = new Node();
        budkov.setName("20.0.0.1");
        Configuration confBudkov = new Configuration();
        confBudkov.setName("confA");
        confBudkov.setDescription("Budkov");
        budkov.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient budkovWC = new Webclient();
        confBudkov.setWebclient(budkovWC);
        budkov.setConfiguration(confBudkov);
        nodesMap.put(budkov.getConfiguration().getDescription(), budkov);
        graph.getNode().add(budkov);

        Node blatna = new Node();
        blatna.setName("20.0.0.2");
        Configuration confBlatna = new Configuration();
        confBlatna.setName("confA");
        confBlatna.setDescription("Blatna");
        blatna.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwBlatna = new Webclient();
        confBlatna.setWebclient(forwBlatna);
        blatna.setConfiguration(confBlatna);
        nodesMap.put(blatna.getConfiguration().getDescription(), blatna);
        graph.getNode().add(blatna);

        Node pribram = new Node();
        pribram.setName("20.0.0.3");
        Configuration confPribram = new Configuration();
        confPribram.setName("confA");
        confPribram.setDescription("Pribram");
        pribram.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwPribram = new Webclient();
        confPribram.setWebclient(forwPribram);
        pribram.setConfiguration(confPribram);
        nodesMap.put(pribram.getConfiguration().getDescription(), pribram);
        graph.getNode().add(pribram);

        Node bereoun = new Node();
        bereoun.setName("20.0.0.4");
        Configuration confBereoun = new Configuration();
        confBereoun.setName("confA");
        confBereoun.setDescription("Bereoun");
        bereoun.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwBereoun = new Webclient();
        confBereoun.setWebclient(forwBereoun);
        bereoun.setConfiguration(confBereoun);
        nodesMap.put(bereoun.getConfiguration().getDescription(), bereoun);
        graph.getNode().add(bereoun);

        Node marianske = new Node();
        marianske.setName("20.0.0.5");
        Configuration confMarianske = new Configuration();
        confMarianske.setName("confA");
        confMarianske.setDescription("Marianske Lazne");
        marianske.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwMarianske = new Webclient();
        confMarianske.setWebclient(forwMarianske);
        marianske.setConfiguration(confMarianske);
        nodesMap.put(marianske.getConfiguration().getDescription(), marianske);
        graph.getNode().add(marianske);

        Node terenzin = new Node();
        terenzin.setName("20.0.0.6");
        Configuration confTerenzin = new Configuration();
        confTerenzin.setName("confA");
        confTerenzin.setDescription("Terenzin");
        terenzin.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwTerenzin = new Webclient();
        confTerenzin.setWebclient(forwTerenzin);
        terenzin.setConfiguration(confTerenzin);
        nodesMap.put(terenzin.getConfiguration().getDescription(), terenzin);
        graph.getNode().add(terenzin);

        Node kralupy = new Node();
        kralupy.setName("20.0.0.7");
        Configuration confKralupy = new Configuration();
        confKralupy.setName("confA");
        confKralupy.setDescription("Kralupy");
        kralupy.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKralupy = new Webclient();
        confKralupy.setWebclient(forwKralupy);
        kralupy.setConfiguration(confKralupy);
        nodesMap.put(kralupy.getConfiguration().getDescription(), kralupy);
        graph.getNode().add(kralupy);

        Node rez = new Node();
        rez.setName("193.84.160.28");
        Configuration confRez = new Configuration();
        confRez.setName("confA");
        confRez.setDescription("Rez");
        rez.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwRez = new Forwarder();
        forwRez.setName(confRez.getDescription());
        confRez.setForwarder(forwRez);
        rez.setConfiguration(confRez);
        nodesMap.put(rez.getConfiguration().getDescription(), rez);
        graph.getNode().add(rez);

        Node jenstejn = new Node();
        jenstejn.setName("20.0.0.99");
        Configuration confJenstejn = new Configuration();
        confJenstejn.setName("confA");
        confJenstejn.setDescription("Jenstejn");
        jenstejn.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwJenstejn = new Forwarder();
        forwJenstejn.setName(confJenstejn.getDescription());
        confJenstejn.setForwarder(forwJenstejn);
        jenstejn.setConfiguration(confJenstejn);
        nodesMap.put(jenstejn.getConfiguration().getDescription(), jenstejn);
        graph.getNode().add(jenstejn);

        Node podebrady = new Node();
        podebrady.setName("20.0.0.9");
        Configuration confPodebrady = new Configuration();
        confPodebrady.setName("confA");
        confPodebrady.setDescription("Podebrady");
        podebrady.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwPodebrady = new Webclient();
        confPodebrady.setWebclient(forwPodebrady);
        podebrady.setConfiguration(confPodebrady);
        nodesMap.put(podebrady.getConfiguration().getDescription(), podebrady);
        graph.getNode().add(podebrady);

        Node kostelec = new Node();
        kostelec.setName("20.0.0.10");
        Configuration confKostelec = new Configuration();
        confKostelec.setName("confA");
        confKostelec.setDescription("Kostelec n.C.L.");
        kostelec.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKostelec = new Webclient();
        confKostelec.setWebclient(forwKostelec);
        kostelec.setConfiguration(confKostelec);
        nodesMap.put(kostelec.getConfiguration().getDescription(), kostelec);
        graph.getNode().add(kostelec);

        Node ondrejov = new Node();
        ondrejov.setName("20.0.0.11");
        Configuration confOndrejov = new Configuration();
        confOndrejov.setName("confA");
        confOndrejov.setDescription("Ondrejov");
        ondrejov.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwOndrejov = new Webclient();
        confOndrejov.setWebclient(forwOndrejov);
        ondrejov.setConfiguration(confOndrejov);
        nodesMap.put(ondrejov.getConfiguration().getDescription(), ondrejov);
        graph.getNode().add(ondrejov);

        Node komorniHradek = new Node();
        komorniHradek.setName("20.0.0.12");
        Configuration confKomorniHradek = new Configuration();
        confKomorniHradek.setName("confA");
        confKomorniHradek.setDescription("Komorni Hradek");
        komorniHradek.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKomorniHradek = new Webclient();
        confKomorniHradek.setWebclient(forwKomorniHradek);
        komorniHradek.setConfiguration(confKomorniHradek);
        nodesMap.put(komorniHradek.getConfiguration().getDescription(), komorniHradek);
        graph.getNode().add(komorniHradek);

        Node dolniBrezany = new Node();
        dolniBrezany.setName("20.0.0.13");
        Configuration confDolniBrezany = new Configuration();
        confDolniBrezany.setName("confA");
        confDolniBrezany.setDescription("Dolni Brezany");
        dolniBrezany.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwDolniBrezany = new Webclient();
        confDolniBrezany.setWebclient(forwDolniBrezany);
        dolniBrezany.setConfiguration(confDolniBrezany);
        nodesMap.put(dolniBrezany.getConfiguration().getDescription(), dolniBrezany);
        graph.getNode().add(dolniBrezany);


        //Node: BRNO
        Node lednice = new Node();
        lednice.setName("20.0.1.99");
        Configuration confLednice = new Configuration();
        confLednice.setName("confA");
        confLednice.setDescription("Lednice");
        lednice.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwLednice = new Forwarder();
        forwLednice.setName(confLednice.getDescription());
        confLednice.setForwarder(forwLednice);
        lednice.setConfiguration(confLednice);
        nodesMap.put(lednice.getConfiguration().getDescription(), lednice);
        graph.getNode().add(lednice);

        Node breclav = new Node();
        breclav.setName("20.0.1.2");
        Configuration confBreclav = new Configuration();
        confBreclav.setName("confA");
        confBreclav.setDescription("Breclav");
        breclav.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwBreclav = new Webclient();
        confBreclav.setWebclient(forwBreclav);
        breclav.setConfiguration(confBreclav);
        nodesMap.put(breclav.getConfiguration().getDescription(), breclav);
        graph.getNode().add(breclav);

        Node kyjov = new Node();
        kyjov.setName("20.0.1.3");
        Configuration confKyjov = new Configuration();
        confKyjov.setName("confA");
        confKyjov.setDescription("Kyjov");
        kyjov.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKyjov = new Webclient();
        confKyjov.setWebclient(forwKyjov);
        kyjov.setConfiguration(confKyjov);
        nodesMap.put(kyjov.getConfiguration().getDescription(), kyjov);
        graph.getNode().add(kyjov);

        Node zlin = new Node();
        zlin.setName("20.0.1.200");
        Configuration confZlin = new Configuration();
        confZlin.setName("confA");
        confZlin.setDescription("Zlin");
        zlin.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwZlin = new Forwarder();
        forwZlin.setName(confZlin.getDescription());
        confZlin.setForwarder(forwZlin);
        zlin.setConfiguration(confZlin);
        nodesMap.put(zlin.getConfiguration().getDescription(), zlin);
        graph.getNode().add(zlin);

        Node uherske = new Node();
        uherske.setName("20.0.1.5");
        Configuration confUherske = new Configuration();
        confUherske.setName("confA");
        confUherske.setDescription("Uherske Hradiste");
        uherske.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwUherske = new Webclient();
        confUherske.setWebclient(forwUherske);
        uherske.setConfiguration(confUherske);
        nodesMap.put(uherske.getConfiguration().getDescription(), uherske);
        graph.getNode().add(uherske);

        Node vyskov = new Node();
        vyskov.setName("20.0.1.6");
        Configuration confVyskov = new Configuration();
        confVyskov.setName("confA");
        confVyskov.setDescription("Vyskov");
        vyskov.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwVyskov = new Webclient();
        confVyskov.setWebclient(forwVyskov);
        vyskov.setConfiguration(confVyskov);
        nodesMap.put(vyskov.getConfiguration().getDescription(), vyskov);
        graph.getNode().add(vyskov);

        Node jihlava = new Node();
        jihlava.setName("195.113.227.169");
        Configuration confJihlava = new Configuration();
        confJihlava.setName("confA");
        confJihlava.setDescription("Jihlava");
        jihlava.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwJihlava = new Forwarder();
        forwJihlava.setName(confJihlava.getDescription());
        confJihlava.setForwarder(forwJihlava);
        jihlava.setConfiguration(confJihlava);
        nodesMap.put(jihlava.getConfiguration().getDescription(), jihlava);
        graph.getNode().add(jihlava);

        Node ostrava = new Node();
        ostrava.setName("158.196.100.101");
        Configuration confOstrava = new Configuration();
        confOstrava.setName("confA");
        confOstrava.setDescription("Ostrava");
        ostrava.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwOstrava = new Forwarder();
        forwOstrava.setName(confOstrava.getDescription());
        confOstrava.setForwarder(forwOstrava);
        ostrava.setConfiguration(confOstrava);
        nodesMap.put(ostrava.getConfiguration().getDescription(), ostrava);
        graph.getNode().add(ostrava);

        Node olomouc = new Node();
        olomouc.setName("195.113.161.188");
        Configuration confOlomouc = new Configuration();
        confOlomouc.setName("confA");
        confOlomouc.setDescription("Olomouc");
        olomouc.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwOlomouc = new Forwarder();
        forwOlomouc.setName(confOlomouc.getDescription());
        confOlomouc.setForwarder(forwOlomouc);
        olomouc.setConfiguration(confOlomouc);
        nodesMap.put(olomouc.getConfiguration().getDescription(), olomouc);
        graph.getNode().add(olomouc);


        //Node: OSTRAVA
        Node opava = new Node();
        opava.setName("20.0.2.4");
        Configuration confOpava = new Configuration();
        confOpava.setName("confA");
        confOpava.setDescription("Opava");
        opava.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwOpava = new Forwarder();
        forwOpava.setName(confOpava.getDescription());
        confOpava.setForwarder(forwOpava);
        opava.setConfiguration(confOpava);
        nodesMap.put(opava.getConfiguration().getDescription(), opava);
        graph.getNode().add(opava);

        Node ceskyTesin = new Node();
        ceskyTesin.setName("20.0.2.1");
        Configuration confCeskyTesin = new Configuration();
        confCeskyTesin.setName("confA");
        confCeskyTesin.setDescription("Cesky Tesin");
        ceskyTesin.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwCeskyTesin = new Webclient();
        confCeskyTesin.setWebclient(forwCeskyTesin);
        ceskyTesin.setConfiguration(confCeskyTesin);
        nodesMap.put(ceskyTesin.getConfiguration().getDescription(), ceskyTesin);
        graph.getNode().add(ceskyTesin);

        Node karniva = new Node();
        karniva.setName("20.0.2.2");
        Configuration confKarniva = new Configuration();
        confKarniva.setName("confA");
        confKarniva.setDescription("Karniva");
        karniva.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKarniva = new Webclient();
        confKarniva.setWebclient(forwKarniva);
        karniva.setConfiguration(confKarniva);
        nodesMap.put(karniva.getConfiguration().getDescription(), karniva);
        graph.getNode().add(karniva);

        //Node: PLZEN
        Node klatovy = new Node();
        klatovy.setName("20.0.3.1");
        Configuration confKlatovy = new Configuration();
        confKlatovy.setName("confA");
        confKlatovy.setDescription("Klatovy");
        klatovy.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKlatovy = new Webclient();
        confKlatovy.setWebclient(forwKlatovy);
        klatovy.setConfiguration(confKlatovy);
        nodesMap.put(klatovy.getConfiguration().getDescription(), klatovy);
        graph.getNode().add(klatovy);

        Node kasperskeHory = new Node();
        kasperskeHory.setName("20.0.3.2");
        Configuration confKasperskeHory = new Configuration();
        confKasperskeHory.setName("confA");
        confKasperskeHory.setDescription("Kasperske Hory");
        kasperskeHory.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKasperskeHory = new Webclient();
        confKasperskeHory.setWebclient(forwKasperskeHory);
        kasperskeHory.setConfiguration(confKasperskeHory);
        nodesMap.put(kasperskeHory.getConfiguration().getDescription(), kasperskeHory);
        graph.getNode().add(kasperskeHory);

        Node cheb = new Node();
        cheb.setName("20.0.4.10");
        Configuration confCheb = new Configuration();
        confCheb.setName("confA");
        confCheb.setDescription("Cheb");
        cheb.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwCheb = new Forwarder();
        forwCheb.setName(confCheb.getDescription());
        confCheb.setForwarder(forwCheb);
        cheb.setConfiguration(confCheb);
        nodesMap.put(cheb.getConfiguration().getDescription(), cheb);
        graph.getNode().add(cheb);

        //Node: LIBEREC
        Node turnov = new Node();
        turnov.setName("20.0.5.1");
        Configuration confTurnov = new Configuration();
        confTurnov.setName("confA");
        confTurnov.setDescription("Turnov");
        turnov.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwTurnov = new Webclient();
        confTurnov.setWebclient(forwTurnov);
        turnov.setConfiguration(confTurnov);
        nodesMap.put(turnov.getConfiguration().getDescription(), turnov);
        graph.getNode().add(turnov);

        Node jabolecnN = new Node();
        jabolecnN.setName("20.0.5.2");
        Configuration confJabolecnN = new Configuration();
        confJabolecnN.setName("confA");
        confJabolecnN.setDescription("Jabolec nad Nisou");
        jabolecnN.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwJabolecnN = new Webclient();
        confJabolecnN.setWebclient(forwJabolecnN);
        jabolecnN.setConfiguration(confJabolecnN);
        nodesMap.put(jabolecnN.getConfiguration().getDescription(), jabolecnN);
        graph.getNode().add(jabolecnN);

        //Node: Usti n.L.
        Node most = new Node();
        most.setName("20.0.6.155");
        Configuration confMost = new Configuration();
        confMost.setName("confA");
        confMost.setDescription("Most");
        most.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwMost = new Forwarder();
        forwMost.setName(confMost.getDescription());
        confMost.setForwarder(forwMost);
        most.setConfiguration(confMost);
        nodesMap.put(most.getConfiguration().getDescription(), most);
        graph.getNode().add(most);

        Node decin = new Node();
        decin.setName("20.0.7.87");
        Configuration confDecin = new Configuration();
        confDecin.setName("confA");
        confDecin.setDescription("Decin");
        decin.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwDecin = new Forwarder();
        forwDecin.setName(confDecin.getDescription());
        confDecin.setForwarder(forwDecin);
        decin.setConfiguration(confDecin);
        nodesMap.put(decin.getConfiguration().getDescription(), decin);
        graph.getNode().add(decin);

        Node litvinov = new Node();
        litvinov.setName("20.0.7.1");
        Configuration confLitvinov = new Configuration();
        confLitvinov.setName("confA");
        confLitvinov.setDescription("Litvinov");
        litvinov.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwLitvinov = new Webclient();
        confLitvinov.setWebclient(forwLitvinov);
        litvinov.setConfiguration(confLitvinov);
        nodesMap.put(litvinov.getConfiguration().getDescription(), litvinov);
        graph.getNode().add(litvinov);


        //Node: HRADEC KRALOVE
        Node letohrad = new Node();
        letohrad.setName("20.0.8.100");
        Configuration confLetohrad = new Configuration();
        confLetohrad.setName("confA");
        confLetohrad.setDescription("Letohrad");
        letohrad.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwLetohrad = new Forwarder();
        forwLetohrad.setName(confLetohrad.getDescription());
        confLetohrad.setForwarder(forwLetohrad);
        letohrad.setConfiguration(confLetohrad);
        nodesMap.put(letohrad.getConfiguration().getDescription(), letohrad);
        graph.getNode().add(letohrad);

        Node litomysi = new Node();
        litomysi.setName("20.0.9.1");
        Configuration confLitomysi = new Configuration();
        confLitomysi.setName("confA");
        confLitomysi.setDescription("Litomysi");
        litomysi.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwLitomysi = new Webclient();
        confLitomysi.setWebclient(forwLitomysi);
        litomysi.setConfiguration(confLitomysi);
        nodesMap.put(litomysi.getConfiguration().getDescription(), litomysi);
        graph.getNode().add(litomysi);

        Node kuks = new Node();
        kuks.setName("20.0.9.2");
        Configuration confKuks = new Configuration();
        confKuks.setName("confA");
        confKuks.setDescription("Kuks");
        kuks.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwKuks = new Webclient();
        confKuks.setWebclient(forwKuks);
        kuks.setConfiguration(confKuks);
        nodesMap.put(kuks.getConfiguration().getDescription(), kuks);
        graph.getNode().add(kuks);

        Node novyHradek = new Node();
        novyHradek.setName("20.0.9.3");
        Configuration confNovyHradek = new Configuration();
        confNovyHradek.setName("confA");
        confNovyHradek.setDescription("Novy Hradek");
        novyHradek.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwNovyHradek = new Webclient();
        confNovyHradek.setWebclient(forwNovyHradek);
        novyHradek.setConfiguration(confNovyHradek);
        nodesMap.put(novyHradek.getConfiguration().getDescription(), novyHradek);
        graph.getNode().add(novyHradek);

        Node cTrebova = new Node();
        cTrebova.setName("20.0.9.4");
        Configuration confCTrebova = new Configuration();
        confCTrebova.setName("confA");
        confCTrebova.setDescription("Ceska Trebova");
        cTrebova.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwCTrebova = new Webclient();
        confCTrebova.setWebclient(forwCTrebova);
        cTrebova.setConfiguration(confCTrebova);
        nodesMap.put(cTrebova.getConfiguration().getDescription(), cTrebova);
        graph.getNode().add(cTrebova);

        //Node: PARDUBICE
        Node lazneBohdanec = new Node();
        lazneBohdanec.setName("20.0.10.1");
        Configuration confLazneBohdanec = new Configuration();
        confLazneBohdanec.setName("confA");
        confLazneBohdanec.setDescription("Lazne Bohdanec");
        lazneBohdanec.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwLazneBohdanec = new Webclient();
        confLazneBohdanec.setWebclient(forwLazneBohdanec);
        lazneBohdanec.setConfiguration(confLazneBohdanec);
        nodesMap.put(lazneBohdanec.getConfiguration().getDescription(), lazneBohdanec);
        graph.getNode().add(lazneBohdanec);

        //Node: OLOMOUC
        Node mTrebova = new Node();
        mTrebova.setName("20.0.11.1");
        Configuration confMTrebova = new Configuration();
        confMTrebova.setName("confA");
        confMTrebova.setDescription("Moravska Trebova");
        mTrebova.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwMTrebova = new Webclient();
        confMTrebova.setWebclient(forwMTrebova);
        mTrebova.setConfiguration(confMTrebova);
        nodesMap.put(mTrebova.getConfiguration().getDescription(), mTrebova);
        graph.getNode().add(mTrebova);

        //Node: CESKE BUDEJOVICE
        Node noveHardy = new Node();
        noveHardy.setName("20.0.12.1");
        Configuration confNoveHardy = new Configuration();
        confNoveHardy.setName("confA");
        confNoveHardy.setDescription("Nove Hardy");
        noveHardy.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwNoveHardy = new Webclient();
        confNoveHardy.setWebclient(forwNoveHardy);
        noveHardy.setConfiguration(confNoveHardy);
        nodesMap.put(noveHardy.getConfiguration().getDescription(), noveHardy);
        graph.getNode().add(noveHardy);

        Node trebon = new Node();
        trebon.setName("20.0.12.2");
        Configuration confTrebon = new Configuration();
        confTrebon.setName("confA");
        confTrebon.setDescription("Trebon");
        trebon.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwTrebon = new Webclient();
        confTrebon.setWebclient(forwTrebon);
        trebon.setConfiguration(confTrebon);
        nodesMap.put(trebon.getConfiguration().getDescription(), trebon);
        graph.getNode().add(trebon);

        Node jindrichuv = new Node();
        jindrichuv.setName("20.0.12.28");
        Configuration confJindrichuv = new Configuration();
        confJindrichuv.setName("confA");
        confJindrichuv.setDescription("Jindrichuv Hradec");
        jindrichuv.setFunctionalType(FunctionalTypes.FORWARDER);
        Forwarder forwJindrichuv = new Forwarder();
        forwJindrichuv.setName(confJindrichuv.getDescription());
        confJindrichuv.setForwarder(forwJindrichuv);
        jindrichuv.setConfiguration(confJindrichuv);
        nodesMap.put(jindrichuv.getConfiguration().getDescription(), jindrichuv);
        graph.getNode().add(jindrichuv);

        Node tabor = new Node();
        tabor.setName("20.0.12.4");
        Configuration confTabor = new Configuration();
        confTabor.setName("confA");
        confTabor.setDescription("Tabor");
        tabor.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwTabor = new Webclient();
        confTabor.setWebclient(forwTabor);
        tabor.setConfiguration(confTabor);
        nodesMap.put(tabor.getConfiguration().getDescription(), tabor);
        graph.getNode().add(tabor);

        Node ponesice = new Node();
        ponesice.setName("20.0.12.5");
        Configuration confPonesice = new Configuration();
        confPonesice.setName("confA");
        confPonesice.setDescription("Ponesice");
        ponesice.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwPonesice = new Webclient();
        confPonesice.setWebclient(forwPonesice);
        ponesice.setConfiguration(confPonesice);
        nodesMap.put(ponesice.getConfiguration().getDescription(), ponesice);
        graph.getNode().add(ponesice);

        Node temelin = new Node();
        temelin.setName("20.0.12.6");
        Configuration confTemelin = new Configuration();
        confTemelin.setName("confA");
        confTemelin.setDescription("Temelin");
        temelin.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwTemelin = new Webclient();
        confTemelin.setWebclient(forwTemelin);
        temelin.setConfiguration(confTemelin);
        nodesMap.put(temelin.getConfiguration().getDescription(), temelin);
        graph.getNode().add(temelin);

        Node pisek = new Node();
        pisek.setName("20.0.12.7");
        Configuration confPisek = new Configuration();
        confPisek.setName("confA");
        confPisek.setDescription("Pisek");
        pisek.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwPisek = new Webclient();
        confPisek.setWebclient(forwPisek);
        pisek.setConfiguration(confPisek);
        nodesMap.put(pisek.getConfiguration().getDescription(), pisek);
        graph.getNode().add(pisek);

        Node vodnany = new Node();
        vodnany.setName("20.0.12.8");
        Configuration confVodnany = new Configuration();
        confVodnany.setName("confA");
        confVodnany.setDescription("Vodnany");
        vodnany.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwVodnany = new Webclient();
        confVodnany.setWebclient(forwVodnany);
        vodnany.setConfiguration(confVodnany);
        nodesMap.put(vodnany.getConfiguration().getDescription(), vodnany);
        graph.getNode().add(vodnany);

        //Node: JIHLAVA
        Node humpolec = new Node();
        humpolec.setName("20.0.13.1");
        Configuration confHumpolec = new Configuration();
        confHumpolec.setName("confA");
        confHumpolec.setDescription("Humpolec");
        humpolec.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwHumpolec = new Webclient();
        confHumpolec.setWebclient(forwHumpolec);
        humpolec.setConfiguration(confHumpolec);
        nodesMap.put(humpolec.getConfiguration().getDescription(), humpolec);
        graph.getNode().add(humpolec);

        Node telc = new Node();
        telc.setName("20.0.14.3");
        Configuration confTelc = new Configuration();
        confTelc.setName("confA");
        confTelc.setDescription("Telc");
        telc.setFunctionalType(FunctionalTypes.WEBCLIENT);
        Webclient forwTelc = new Webclient();
        confTelc.setWebclient(forwTelc);
        telc.setConfiguration(confTelc);
        nodesMap.put(telc.getConfiguration().getDescription(), telc);
        graph.getNode().add(telc);

        // NODE CONNECTION

        connect(praha, brno);
        connect(praha, pardubice);
        connect(praha, ustiNL);
        connect(praha, plzen);
        connect(praha, hradecKralove);
        connect(praha, liberec);
        connect(praha, ceskeBud);
        connect(praha, budkov);
        connect(praha, blatna);
        connect(praha, pribram);
        connect(praha, bereoun);
        connect(praha, marianske);
        connect(praha, terenzin);
        connect(praha, kralupy);
        connect(praha, rez);
        connect(praha, jenstejn);
        connect(rez, jenstejn);
        connect(praha, podebrady);
        connect(praha, kostelec);
        connect(praha, ondrejov);
        connect(praha, komorniHradek);
        connect(praha, dolniBrezany);
        connect(brno, jihlava);
        connect(brno, ostrava);
        connect(brno, olomouc);
        connect(brno, lednice);
        connect(lednice, breclav);
        connect(brno, kyjov);
        connect(brno, zlin);
        connect(zlin, uherske);
        connect(brno, vyskov);
        connect(ostrava, olomouc);
        connect(ostrava, opava);
        connect(ostrava, ceskyTesin);
        connect(ostrava, karniva);
        connect(plzen, ceskeBud);
        connect(plzen, cheb);
        connect(plzen, klatovy);
        connect(plzen, kasperskeHory);
        connect(liberec, ustiNL);
        connect(liberec, turnov);
        connect(liberec, jabolecnN);
        connect(ustiNL, most);
        connect(most, cheb);
        connect(ustiNL, decin);
        connect(most, decin);
        connect(ustiNL, litvinov);
        connect(hradecKralove, pardubice);
        connect(hradecKralove, letohrad);
        connect(letohrad, opava);
        connect(letohrad, olomouc);
        connect(hradecKralove, litomysi);
        connect(hradecKralove, kuks);
        connect(hradecKralove, novyHradek);
        connect(hradecKralove, cTrebova);
        connect(hradecKralove, liberec);
        connect(pardubice, lazneBohdanec);
        connect(olomouc, zlin);
        connect(olomouc, mTrebova);
        connect(ceskeBud, noveHardy);
        connect(ceskeBud, trebon);
        connect(ceskeBud, jindrichuv);
        connect(jindrichuv, jihlava);
        connect(ceskeBud, tabor);
        connect(ceskeBud, ponesice);
        connect(ceskeBud, temelin);
        connect(ceskeBud, pisek);
        connect(ceskeBud, vodnany);
        connect(jihlava, humpolec);
        connect(jihlava, telc);
    }

    /**
     * Selects random source and destination nodes based on predefined percentages.
     *
     * The methods:
     * - Computes node distribution percentages for main cities and others.
     * - Categorizes nodes by city.
     * - Selects nodes randomly respecting these percentages.
     * - Allows node reuse in sources and destinations.
     * - Ensures destination nodes differ from their corresponding source nodes.
     * - Avoids duplicate or inverted source-destination pairs.
     * - If not enough nodes are selected initially, fills the gap prioritizing nodes from the four main cities.
     *
     * @return map of selected nodes, where the key is the source, and the value is the list of destinations
     */
    public void nodesPercentage(int policyNumber) {
        this.mainNodes = (int) Math.round((policyNumber * 2) * 0.9);
        this.others = (policyNumber * 2) - mainNodes;

        this.prahaPercent = (int) Math.round(this.mainNodes * 0.8);
        this.ustiPercent = (int) Math.round(this.mainNodes * 0.1);
        this.ostravaPercent = (int) Math.round(this.mainNodes * 0.05);
        this.brnoPercent = (int) Math.round(this.mainNodes * 0.05);
    }

    public static class NodeSelectorResult {
        public final List<Node> sources;
        public final List<Node> destinations;

        public NodeSelectorResult(List<Node> sources, List<Node> destinations) {
            this.sources = sources;
            this.destinations = destinations;
        }
    }

    public Map<Node, List<Node>> selectRandomNodes(Graph graph, int policyNumber, Constraints cnst) {
        nodesPercentage(policyNumber);

        List<Node> prahaNodes = new ArrayList<>();
        List<Node> ustiNodes = new ArrayList<>();
        List<Node> ostravaNodes = new ArrayList<>();
        List<Node> brnoNodes = new ArrayList<>();
        List<Node> othersNodes = new ArrayList<>();

        for (Node node : nodesMap.values()) {
            switch (node.getName()) {
                case "146.102.200.120":
                    prahaNodes.add(node);
                    break;
                case "195.113.198.50":
                    ustiNodes.add(node);
                    break;
                case "158.196.100.101":
                    ostravaNodes.add(node);
                    break;
                case "147.251.100.25":
                    brnoNodes.add(node);
                    break;
                default:
                    othersNodes.add(node);
                    break;
            }
        }

        List<Node> selectedMainNodes = new ArrayList<>();
        Random random = new Random();

        selectedMainNodes.addAll(pickRandomNodes(prahaNodes, prahaPercent, random));
        selectedMainNodes.addAll(pickRandomNodes(ustiNodes, ustiPercent, random));
        selectedMainNodes.addAll(pickRandomNodes(ostravaNodes, ostravaPercent, random));
        selectedMainNodes.addAll(pickRandomNodes(brnoNodes, brnoPercent, random));

        List<Node> selectedOthers = pickRandomNodes(othersNodes, others, random);

        List<Node> combined = new ArrayList<>();
        combined.addAll(selectedMainNodes);
        combined.addAll(selectedOthers);

        if (combined.size() < policyNumber * 2) {
            List<Node> fallbackAll = new ArrayList<>();
            fallbackAll.addAll(prahaNodes);
            fallbackAll.addAll(ustiNodes);
            fallbackAll.addAll(ostravaNodes);
            fallbackAll.addAll(brnoNodes);
            fallbackAll.addAll(othersNodes);
            fallbackAll.removeAll(combined);

            Collections.shuffle(fallbackAll, random);

            int needed = (policyNumber * 2) - combined.size();

            combined.addAll(fallbackAll.subList(0, Math.min(needed, fallbackAll.size())));
        }

        Collections.shuffle(combined, random);

        List<Node> sources = new ArrayList<>();
        for (int i = 0; i < policyNumber; i++) {
            sources.add(combined.get(random.nextInt(combined.size())));
        }

        List<Node> destinationCandidates = new ArrayList<>(combined);
        Set<String> pairsSeen = new HashSet<>();
        Map<Node, List<Node>> resultMap = new HashMap<>();

        for (Node source : sources) {
            resultMap.put(source, new ArrayList<>());
        }

        int baseDestPerSource = policyNumber / policyNumber;
        int extra = policyNumber % policyNumber;

        for (int i = 0; i < sources.size(); i++) {
            Node sourceNode = sources.get(i);
            int destCount = baseDestPerSource + (i < extra ? 1 : 0);

            List<Node> destinations = resultMap.get(sourceNode);

            int assigned = 0;
            int attempts = 0;
            while (assigned < destCount && attempts < combined.size() * 2) {
                Node candidate = destinationCandidates.get(random.nextInt(destinationCandidates.size()));

                if (!candidate.equals(sourceNode)) {
                    String pair = sourceNode.getName() + "-" + candidate.getName();
                    String inverse = candidate.getName() + "-" + sourceNode.getName();

                    if (!pairsSeen.contains(pair) && !pairsSeen.contains(inverse) && !destinations.contains(candidate)) {
                        destinations.add(candidate);
                        pairsSeen.add(pair);
                        assigned++;
                    }
                }
                attempts++;
            }

            if (assigned < destCount) {
                for (Node candidate : destinationCandidates) {
                    if (assigned >= destCount) break;

                    if (!candidate.equals(sourceNode) && !destinations.contains(candidate)) {
                        String pair = sourceNode.getName() + "-" + candidate.getName();
                        String inverse = candidate.getName() + "-" + sourceNode.getName();

                        if (!pairsSeen.contains(pair) && !pairsSeen.contains(inverse)) {
                            destinations.add(candidate);
                            pairsSeen.add(pair);
                            assigned++;
                        }
                    }
                }
            }
        }

        HashMap<Node, List<Node>> modifiedResultMap = new HashMap<>();

        for (Map.Entry<Node, List<Node>> entry : resultMap.entrySet()) {
            Node originalSource = entry.getKey();
            List<Node> originalDestList = entry.getValue();

            Node sourceWebClient = originalSource;

            // If the source is a FORWARDER, create a WEBCLIENT, that is attached to the FORWARDER, and set it as the source of the traffic
            if (originalSource.getFunctionalType().equals(FunctionalTypes.FORWARDER)) {
                String originalSourceIp = originalSource.getName();

                sourceWebClient = nodesMap.get(originalSource.getConfiguration().getDescription()+" EP");

                if (sourceWebClient == null) {
                    sourceWebClient = new Node();
                    sourceWebClient.setName(originalSourceIp);
                    Configuration conf = new Configuration();
                    conf.setName("ConfA");
                    conf.setDescription(originalSource.getConfiguration().getDescription() + " EP");
                    sourceWebClient.setFunctionalType(FunctionalTypes.WEBCLIENT);

                    Webclient wc = new Webclient();
                    conf.setWebclient(wc);
                    sourceWebClient.setConfiguration(conf);

                    nodesMap.put(sourceWebClient.getConfiguration().getDescription(), sourceWebClient);
                    graph.getNode().add(sourceWebClient);

                    List<Node> neighbours = new ArrayList<>();
                    originalSource.getNeighbour().forEach(x ->
                            nodesMap.values().stream()
                                    .filter(n -> n.getName().equals(x.getName()))
                                    .findFirst()
                                    .ifPresent(neighbours::add)
                    );

                    neighbours.forEach(x -> disconnect(x, originalSource));

                    String incrementedSourceIp = incrementIp(originalSourceIp);
                    originalSource.setName(incrementedSourceIp);

                    neighbours.forEach(x -> connect(x, originalSource));
                    connect(originalSource, sourceWebClient);
                }
            }

            List<Node> modifiedDestList = new ArrayList<>();

            // If the destination is a FORWARDER, create a WEBCLIENT, that is attached to the FORWARDER, and set it as the destination of the traffic
            for (Node originalDest : originalDestList) {
                String originalDestIp = originalDest.getName();

                Node destWebClient = originalDest;

                if (originalDest.getFunctionalType().equals(FunctionalTypes.FORWARDER)) {
                    destWebClient = nodesMap.get(originalDest.getConfiguration().getDescription()+" EP");
                    if (destWebClient == null) {
                        destWebClient = new Node();
                        destWebClient.setName(originalDestIp);

                        Configuration confD = new Configuration();
                        confD.setName("ConfA");
                        confD.setDescription(originalDest.getConfiguration().getDescription() + " EP");
                        destWebClient.setFunctionalType(FunctionalTypes.WEBCLIENT);

                        Webclient ws = new Webclient();
                        confD.setWebclient(ws);
                        destWebClient.setConfiguration(confD);

                        nodesMap.put(destWebClient.getConfiguration().getDescription(), destWebClient);
                        graph.getNode().add(destWebClient);

                        List<Node> neighbours = new ArrayList<>();
                        originalDest.getNeighbour().forEach(x ->
                                nodesMap.values().stream()
                                        .filter(n -> n.getName().equals(x.getName()))
                                        .findFirst()
                                        .ifPresent(neighbours::add)
                        );

                        neighbours.forEach(x -> disconnect(x, originalDest));

                        String incrementedDestIp = incrementIp(originalDestIp);
                        originalDest.setName(incrementedDestIp);

                        neighbours.forEach(x -> connect(x, originalDest));
                        connect(originalDest, destWebClient);
                    }
                }
                modifiedDestList.add(destWebClient);
            }
            modifiedResultMap.put(sourceWebClient, modifiedDestList);
        }


        // Add constraints to avoid the allocation of a firewall in the specified link
        AllocationConstraints acs = new AllocationConstraints();
        List<String> declaredConstraints = new ArrayList<>();

        modifiedResultMap.forEach((src, dstList) -> {
            if (src.getConfiguration().getDescription().contains("EP")) {
                if (!declaredConstraints.contains(src.getName())) {
                    AllocationConstraint ac1 = new AllocationConstraint();
                    ac1.setType(AllocationConstraintType.FORBIDDEN);
                    ac1.setNodeA(src.getNeighbour().get(0).getName());
                    ac1.setNodeB(src.getName());
                    acs.getAllocationConstraint().add(ac1);
                    declaredConstraints.add(src.getName());
                }
            }

            dstList.forEach(dst -> {
                if (dst.getConfiguration().getDescription().contains("EP")) {
                    if (!declaredConstraints.contains(dst.getName())) {
                        AllocationConstraint ac = new AllocationConstraint();
                        ac.setType(AllocationConstraintType.FORBIDDEN);
                        ac.setNodeA(dst.getNeighbour().get(0).getName());
                        ac.setNodeB(dst.getName());
                        acs.getAllocationConstraint().add(ac);
                        declaredConstraints.add(dst.getName());
                    }
                }
            });
        });

        cnst.setAllocationConstraints(acs);

        return modifiedResultMap;
    }

    private String incrementIp(String ip) {
        String[] parts = ip.split("\\.");
        if(parts.length != 4) return ip;

        int last = Integer.parseInt(parts[3]);
        last = (last + 1) % 256;

        return parts[0] + "." + parts[1] + "." + parts[2] + "." + last;
    }


    private List<Node> pickRandomNodes(List<Node> fromList, int count, Random rand) {
        List<Node> copy = new ArrayList<>(fromList);
        Collections.shuffle(copy, rand);
        return copy.subList(0, Math.min(count, copy.size()));
    }


    /**
     * Create the policies for the network.
     *
     * @param selectedNodes is a map of nodes selected for the policy generation. The key is the source, the value is the list of destinations
     * @param policyNumber is the number of policies to generate
     * @param isolationBidirectional is a flag to specify if the isolation policies must be generated for both the directions
     * @param reachabilityPerc specifies the percentage of reachability policies to generate
     * @param usePorts specifies to declare the source and destination ports
     */
    public void createPolicy(Map<Node, List<Node>> selectedNodes, int policyNumber, boolean isolationBidirectional, Double reachabilityPerc, Boolean usePorts) {
        int reachability = (int) Math.round(policyNumber*reachabilityPerc);
        int reachabilityNumber = (int) Math.round(reachability*0.6);
        int completeReachNumber = reachability - reachabilityNumber;
        int isolationNumber = policyNumber - reachability;

        List<Map.Entry<Node, Node>> couples = new ArrayList<>();

        for (Map.Entry<Node, List<Node>> entry : selectedNodes.entrySet()) {
            Node key = entry.getKey();
            List<Node> lista = entry.getValue();

            for (Node val : lista) {
                couples.add(new AbstractMap.SimpleEntry<>(key, val));
            }
        }

        int reachabilityCounter = 0;

        for (int i = 0; i < couples.size(); i++) {
            if (reachabilityCounter > reachabilityNumber-1) break;

            Property property = new Property();
            property.setName(PName.REACHABILITY_PROPERTY);
            property.setGraph((long) 0);
            property.setSrc(couples.get(i).getKey().getName());
            property.setDst(couples.get(i).getValue().getName());

            if (usePorts) {
                property.setSrcPort("2852");
                property.setDstPort("443");
            }

            nfv.getPropertyDefinition().getProperty().add(property);

            Property property2 = new Property();
            property2.setName(PName.REACHABILITY_PROPERTY);
            property2.setGraph((long) 0);
            property2.setSrc(couples.get(i).getValue().getName());
            property2.setDst(couples.get(i).getKey().getName());

            if (usePorts) {
                property2.setSrcPort("443");
                property2.setDstPort("2852");
            }

            nfv.getPropertyDefinition().getProperty().add(property2);

            reachabilityCounter++;
        }

        int complReachabilityCounter = 0;

        for (int i = reachabilityNumber; i < couples.size(); i++) {
            if (complReachabilityCounter > completeReachNumber-1) break;

            Property property = new Property();
            property.setName(PName.COMPLETE_REACHABILITY_PROPERTY);
            property.setGraph((long) 0);
            property.setSrc(couples.get(i).getKey().getName());
            property.setDst(couples.get(i).getValue().getName());

            if (usePorts) {
                property.setSrcPort("2852");
                property.setDstPort("443");
            }

            nfv.getPropertyDefinition().getProperty().add(property);

            Property property2 = new Property();
            property2.setName(PName.COMPLETE_REACHABILITY_PROPERTY);
            property2.setGraph((long) 0);
            property2.setSrc(couples.get(i).getValue().getName());
            property2.setDst(couples.get(i).getKey().getName());

            if (usePorts) {
                property2.setSrcPort("443");
                property2.setDstPort("2852");
            }

            nfv.getPropertyDefinition().getProperty().add(property2);

            complReachabilityCounter++;
        }

        int isolationCounter = 0;
        for (int i = reachability; i < couples.size(); i++) {
            if (isolationCounter > isolationNumber-1) break;

            Property property = new Property();
            property.setName(PName.ISOLATION_PROPERTY);
            property.setGraph((long) 0);
            property.setSrc(couples.get(i).getKey().getName());
            property.setDst(couples.get(i).getValue().getName());
            nfv.getPropertyDefinition().getProperty().add(property);
            reachabilityCounter++;

            if (isolationBidirectional) {
                Property property2 = new Property();
                property2.setName(PName.ISOLATION_PROPERTY);
                property2.setGraph((long) 0);
                property2.setSrc(couples.get(i).getValue().getName());
                property2.setDst(couples.get(i).getKey().getName());
                nfv.getPropertyDefinition().getProperty().add(property2);
            }
        }
    }

    public int getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(int policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public NFV getNFV() {
        return nfv;
    }

    public void setNFV(NFV nfv) {
        this.nfv = nfv;
    }

    public Boolean getUsePorts() {
        return usePorts;
    }

    public void setUsePorts(Boolean usePorts) {
        this.usePorts = usePorts;
    }
}