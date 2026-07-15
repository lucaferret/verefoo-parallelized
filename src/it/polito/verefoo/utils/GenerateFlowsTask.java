package it.polito.verefoo.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import it.polito.verefoo.allocation.AllocationNodeAP;
import it.polito.verefoo.graph.FlowPathAP;
import it.polito.verefoo.graph.Predicate;
import it.polito.verefoo.graph.SecurityRequirement;
import it.polito.verefoo.jaxb.FunctionalTypes;
import it.polito.verefoo.jaxb.L4ProtocolTypes;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.Property;

public class GenerateFlowsTask implements Runnable {

	SecurityRequirement requirement;
	HashMap<Integer, Predicate> networkAtomicPredicates;
	APUtilsAP aputils;
	HashMap<String, Node> transformersNode;
	AtomicInteger atomicId;

	// OPTIMIZATION: Cache to prevent instantiating millions of Predicates during recursion
	private Map<String, Predicate> destPredicatesCache = new HashMap<>();

	public GenerateFlowsTask(SecurityRequirement requirement, HashMap<Integer, Predicate> networkAtomicPredicates,
	                         APUtilsAP aputils, HashMap<String, Node> transformersNode, AtomicInteger atomicId) {
		this.requirement = requirement;
		this.networkAtomicPredicates = networkAtomicPredicates;
		this.aputils = aputils;
		this.transformersNode = transformersNode;
		this.atomicId = atomicId;
	}

	@Override
	public void run() {
		Property prop = requirement.getOriginalProperty();
		String pSrc = prop.getSrcPort() != null &&  !prop.getSrcPort().equals("null") ? prop.getSrcPort() : "*";
		Predicate srcPredicate = new Predicate(prop.getSrc(), false, "*", false, pSrc, false, "*", false, L4ProtocolTypes.ANY);
		List<Integer> srcPredicateList = new ArrayList<>();
		for(HashMap.Entry<Integer, Predicate> apEntry: networkAtomicPredicates.entrySet()) {
			Predicate intersectionPredicate = aputils.computeIntersection(apEntry.getValue(), srcPredicate);
			if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, apEntry.getValue())
					&& !apEntry.getValue().hasIPDstOnlyNegs()) {
				srcPredicateList.add(apEntry.getKey());
			}
		}

		List<Integer> dstPredicateList = new ArrayList<>();
		String pDst = prop.getDstPort() != null &&  !prop.getDstPort().equals("null") ? prop.getDstPort() : "*";
		Predicate dstPredicate = new Predicate("*", false, prop.getDst(), false, "*", false, pDst, false, prop.getLv4Proto());
		for(HashMap.Entry<Integer, Predicate> apEntry: networkAtomicPredicates.entrySet()) {
			Predicate intersectionPredicate = aputils.computeIntersection(apEntry.getValue(), dstPredicate);
			if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, apEntry.getValue())) {
				dstPredicateList.add(apEntry.getKey());
			}
		}

		for(FlowPathAP flow: requirement.getFlowsMapAP().values()) {
			List<AllocationNodeAP> path = flow.getPath();
			List<List<Integer>> resultList = new ArrayList<>();
			List<List<Integer>> resultListToDiscard = new ArrayList<>();
			int nodeIndex = 0;

			for(Integer ap: srcPredicateList) {
				// We instantiate ONE list per source predicate, and backtrack it through the recursion
				List<Integer> currentList = new ArrayList<>();
				recursiveGenerateAtomicPath(nodeIndex, requirement, path, ap, dstPredicateList, resultList, resultListToDiscard, currentList);
			}

			for(List<Integer> atomicFlow: resultList) {
				flow.addAtomicFlow(atomicId.incrementAndGet(), atomicFlow);
			}
			for(List<Integer> atomicFlowToDiscard: resultListToDiscard)
				flow.addAtomicFlowToDiscard(atomicId.incrementAndGet(), atomicFlowToDiscard);
		}
	}

	private void recursiveGenerateAtomicPath(int nodeIndex, SecurityRequirement sr, List<AllocationNodeAP> path, int ap, List<Integer> dstPredicateList, List<List<Integer>> atomicFlowsList, List<List<Integer>> atomicFlowsListToDiscard, List<Integer> currentList) {
		AllocationNodeAP currentNode = path.get(nodeIndex);
		Predicate currentPredicate = networkAtomicPredicates.get(ap);

		// OPTIMIZATION: Get from O(1) Cache instead of allocating new Predicate objects
		Predicate currentNodeDestPredicate = destPredicatesCache.computeIfAbsent(
				currentNode.getIpAddress(),
				ip -> new Predicate("*", false, ip, false, "*", false, "*", false, L4ProtocolTypes.ANY)
		);

		if(nodeIndex == path.size() -1) {
			if(dstPredicateList.contains(ap)) {
				// OPTIMIZATION: Only copy the array when a final, valid path is found
				atomicFlowsList.add(new ArrayList<>(currentList));
				return;
			} else {
				currentList.add(ap);
				atomicFlowsListToDiscard.add(new ArrayList<>(currentList));
				currentList.remove(currentList.size() - 1); // backtrack
				return;
			}
		}

		Predicate intersectionPredicate = aputils.computeIntersection(currentPredicate, currentNodeDestPredicate);
		if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, currentPredicate)
				&& (currentNode.getTransformationMap().isEmpty()
				|| (!currentNode.getTransformationMap().containsKey(ap)))) {
			currentList.add(ap);
			atomicFlowsListToDiscard.add(new ArrayList<>(currentList));
			currentList.remove(currentList.size() - 1); // backtrack
			return;
		}

		if(transformersNode.containsKey(currentNode.getIpAddress()) && transformersNode.get(currentNode.getIpAddress()).getFunctionalType().equals(FunctionalTypes.NAT)) {
			if(currentNode.getTransformationMap().containsKey(ap)) {
				for(Integer newAp: currentNode.getTransformationMap().get(ap)) {
					// OPTIMIZATION: Backtracking (add, recurse, remove)
					currentList.add(newAp);
					recursiveGenerateAtomicPath(nodeIndex+1, sr, path, newAp, dstPredicateList, atomicFlowsList, atomicFlowsListToDiscard, currentList);
					currentList.remove(currentList.size() - 1);
				}
			} else {
				currentList.add(ap);
				recursiveGenerateAtomicPath(nodeIndex+1, sr, path, ap, dstPredicateList, atomicFlowsList, atomicFlowsListToDiscard, currentList);
				currentList.remove(currentList.size() - 1);
			}
		}
		else {
			currentList.add(ap);
			recursiveGenerateAtomicPath(nodeIndex+1, sr, path, ap, dstPredicateList, atomicFlowsList, atomicFlowsListToDiscard, currentList);
			currentList.remove(currentList.size() - 1);
		}
	}
}