package it.polito.verefoo.partitioning;

import java.util.List;

import it.polito.verefoo.jaxb.Graph;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.Property;

import com.gurobi.gurobi.*;

public class GurobiClusterer {
    private NFV nfvRoot;
    private List<Node> nodes;
    private List<Property> properties;

    public GurobiClusterer(NFV root) {
        this.nfvRoot = root;

        // Extract the nodes from the Java object
        Graph mainGraph = nfvRoot.getGraphs().getGraph().get(0);
        this.nodes = mainGraph.getNode();

        // Extract properites from the Java object
        this.properties = nfvRoot.getPropertyDefinition().getProperty();
    }

    public void solve_ILP() {
    }
}
