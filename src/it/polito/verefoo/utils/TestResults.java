package it.polito.verefoo.utils;

public class TestResults {

	private String z3Result;
	private long atomicPredCompTime;
	private long atomicFlowsCompTime;
	private long beginMaxSMTTime;
	private long totalFlows;
	private long maximalFlowsCompTime;
	private long startMaxSMTtime;
	private long endMaxSMTtims;
	private int totalNumberGeneratedFlows;

	 private long gurobiTime = 0;
	 private int numberOfClusters = 0;
	 private long startMergeTime = 0;
	 private long endMergeTime = 0;
	 private int allocatedFirewalls = 0;
	 private double averageRules = 0.0;
	 private long clusteredSMTTime = 0;
	 private int totalRules = 0;
	 private int allocationPlaces = 0;

	public TestResults() {	
	}

	/*******************************************************************ATOMIC PREDICATE***************************************************************************/
	public long getAtomicPredCompTime() {
		return atomicPredCompTime;
	}
	
	public void setAtomicPredCompTime(long atomicPredCompTime) {
		this.atomicPredCompTime = atomicPredCompTime;
	}
	
	public long getAtomicFlowsCompTime() {
		return atomicFlowsCompTime;
	}
	
	public void setAtomicFlowsCompTime(long atomicFlowsCompTime) {
		this.atomicFlowsCompTime = atomicFlowsCompTime;
	}
	
	public long getBeginMaxSMTTime() {
		return beginMaxSMTTime;
	}

	public void setBeginMaxSMTTime(long beginMaxSMTTime) {
		this.beginMaxSMTTime = beginMaxSMTTime;
	}
	
	public long getTotalFlows() {
		return totalFlows;
	}

	public void setTotalFlows(long totalFlows) {
		this.totalFlows = totalFlows;
	}
	
/******************************************************************MAXIMAL FLOWS*******************************************************************************/
	public long getMaximalFlowsCompTime() {
		return maximalFlowsCompTime;
	}
	
	public void setMaximalFlowsCompTime(long maximalFlowsCompTime) {
		this.maximalFlowsCompTime = maximalFlowsCompTime;
	}


	
	public long getStartMaxSMTtime() {
		return startMaxSMTtime;
	}

	public void setStartMaxSMTtime(long startMaxSMTtime) {
		this.startMaxSMTtime = startMaxSMTtime;
	}

	public long getEndMaxSMTtims() {
		return endMaxSMTtims;
	}

	public void setEndMaxSMTtims(long endMaxSMTtims) {
		this.endMaxSMTtims = endMaxSMTtims;
	}

	public int getTotalNumberGeneratedFlows() {
		return totalNumberGeneratedFlows;
	}

	public void setTotalNumberGeneratedFlows(int totalNumberGeneratedFlows) {
		this.totalNumberGeneratedFlows = totalNumberGeneratedFlows;
	}

	public String getZ3Result() {
		return z3Result;
	}

	public void setZ3Result(String z3Result) {
		this.z3Result = z3Result;
	}

	// Gurobi results getters/setters
	public long getGurobiTime() { return gurobiTime; }
	public void setGurobiTime(long gurobiTime) { this.gurobiTime = gurobiTime; }

	public int getNumberOfClusters() { return numberOfClusters; }
	public void setNumberOfClusters(int numberOfClusters) { this.numberOfClusters = numberOfClusters; }

	 public long getStartMergeTime() { return startMergeTime; }
	 public void setStartMergeTime(long startMergeTime) { this.startMergeTime = startMergeTime; }

	 public long getEndMergeTime() { return endMergeTime; }
	 public void setEndMergeTime(long endMergeTime) { this.endMergeTime = endMergeTime; }
	 public long getMergeTime() { return this.endMergeTime - this.startMergeTime; }

	 public int getAllocatedFirewalls() { return allocatedFirewalls; }
	 public void setAllocatedFirewalls(int allocatedFirewalls) { this.allocatedFirewalls = allocatedFirewalls; }

	 public double getAverageRules() { return averageRules; }
	 public void setAverageRules(double averageRules) { this.averageRules = averageRules; }

	 public long getClusteredSMTTime() { return clusteredSMTTime; }
	 public void setClusteredSMTTime(long clusteredSMTTime) { this.clusteredSMTTime = clusteredSMTTime; }
	 public int getTotalRules() { return totalRules; }
	 public void setTotalRules(int totalRules) { this.totalRules = totalRules; }

	 public int getAllocationPlaces() { return allocationPlaces; }
	 public void setAllocationPlaces(int allocationPlaces) { this.allocationPlaces = allocationPlaces; }
}

