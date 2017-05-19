/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */


package org.matsim.pt2matsim.mapping.pseudoRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;

import java.util.*;

/**
 * @author polettif
 */
public class PseudoGraphImpl implements PseudoGraph {

	/*package*/ static final String SOURCE = "SOURCE";
	/*package*/ static final String DESTINATION = "DESTINATION";
	protected static Logger log = Logger.getLogger(PseudoGraphImpl.class);
	private final Id<PseudoRouteStop> SOURCE_ID = Id.create(SOURCE, PseudoRouteStop.class);
	private final PseudoRouteStop SOURCE_PSEUDO_STOP = new PseudoRouteStopImpl(SOURCE);
	private final Id<PseudoRouteStop> DESTINATION_ID = Id.create(DESTINATION, PseudoRouteStop.class);
	private final PseudoRouteStop DESTINATION_PSEUDO_STOP = new PseudoRouteStopImpl(DESTINATION);

	private final Map<Id<PseudoRouteStop>, PseudoRouteStop> graph;
	private boolean dijkstraComplete = false;
	private LinkedList<PseudoRouteStop> leastCostPath = null;
	private List<Id<Link>> networkLinkIds = new ArrayList<>();
	private Map<String, List<Link>> stopPairLinks = new HashMap<>();
	private Collection<ArtificialLink> artificialNetworkLinks = new HashSet<>();

	public PseudoGraphImpl() {
		this.graph = new HashMap<>();
	}

	/**
	 * Runs dijkstra using a specified source vertex
	 */
	private void runDijkstra() {
		if(!graph.containsKey(SOURCE_ID)) {
			System.err.printf("Graph doesn't contain dummy PseudoRouteStop \"%s\"\n", SOURCE_ID);
			return;
		}

		NavigableSet<PseudoRouteStop> queue = new TreeSet<>();

		queue.add(graph.get(SOURCE_ID));

		PseudoRouteStop currentStop, neighbour;
		while(!queue.isEmpty()) {
			currentStop = queue.pollFirst(); // vertex with shortest distance (first iteration will return source)

			//look at distances to each neighbour
			for(Map.Entry<PseudoRouteStop, Double> n : currentStop.getNeighbours().entrySet()) {
				neighbour = n.getKey(); //the neighbour in this iteration

				final double alternateDist = currentStop.getTravelCostToSource() + n.getValue();
				if(alternateDist < neighbour.getTravelCostToSource()) { // shorter leastCostPath to neighbour found
					queue.remove(neighbour);
					neighbour.setTravelCostToSource(alternateDist);
					neighbour.setClosestPrecedingRouteStop(currentStop);
					queue.add(neighbour);
				}
			}
		}
		dijkstraComplete = true;

		/*
		  returns a leastCostPath from the source to the destination
		 */
		if(!graph.containsKey(DESTINATION_ID)) {
			System.err.printf("Graph doesn't contain end PseudoRouteStop \"%s\"\n", DESTINATION_ID);
		}

		PseudoRouteStop step = graph.get(DESTINATION_ID);
		leastCostPath = new LinkedList<>();

		// check if a leastCostPath exists
		if(step.getClosestPrecedingRouteStop() == null) {
			leastCostPath = null;
		}
		leastCostPath.add(step);
		while(!step.getId().equals(SOURCE_ID)) {
			step = step.getClosestPrecedingRouteStop();
			leastCostPath.add(step);
		}

		// Put it into the correct order
		Collections.reverse(leastCostPath);
		// remove dummies
		leastCostPath.removeFirst();
		leastCostPath.removeLast();

		/*
		  Fetch network links for least cost path
		 */
		networkLinkIds.add(leastCostPath.get(0).getLinkId());
		for(int i = 0; i < leastCostPath.size() - 1; i++) {
			PseudoRouteStop stopA = leastCostPath.get(i);
			PseudoRouteStop stopB = leastCostPath.get(i + 1);

			for(Link l : stopPairLinks.get(getKey(stopA, stopB))) {
				networkLinkIds.add(l.getId());
				if(l instanceof ArtificialLink) {
					artificialNetworkLinks.add((ArtificialLink) l);
				}
			}
			networkLinkIds.add(stopB.getLinkId());
		}
	}

	@Override
	public void addDummyEdges(List<TransitRouteStop> transitRouteStops, Collection<LinkCandidate> firstStopLinkCandidates, Collection<LinkCandidate> lastStopLinkCandidates) {
		for(LinkCandidate lc : firstStopLinkCandidates) {
			addEdge(SOURCE_PSEUDO_STOP, new PseudoRouteStopImpl(0, transitRouteStops.get(0), lc), 1.0, null, true);
		}
		int last = transitRouteStops.size() - 1;
		for(LinkCandidate lc : lastStopLinkCandidates) {
			addEdge(new PseudoRouteStopImpl(last, transitRouteStops.get(last), lc), DESTINATION_PSEUDO_STOP, 1.0, null, true);
		}
	}

	@Override
	public List<PseudoRouteStop> getLeastCostStopSequence() {
		if(!dijkstraComplete) runDijkstra();
		return this.leastCostPath;
	}

	@Override
	public List<Id<Link>> getNetworkLinkIds() {
		if(!dijkstraComplete) runDijkstra();
		return this.networkLinkIds;
	}

	@Override
	public Collection<ArtificialLink> getArtificialNetworkLinks() {
		if(!dijkstraComplete) runDijkstra();
		return this.artificialNetworkLinks;
	}

	@Override
	public void addEdge(int orderOfFromStop, TransitRouteStop fromTransitRouteStop, LinkCandidate fromLinkCandidate, TransitRouteStop toTransitRouteStop, LinkCandidate toLinkCandidate, double pathTravelCost, List<Link> links) {
		PseudoRouteStop fromPseudoStop = new PseudoRouteStopImpl(orderOfFromStop, fromTransitRouteStop, fromLinkCandidate);
		PseudoRouteStop toPseudoStop = new PseudoRouteStopImpl(orderOfFromStop+1, toTransitRouteStop, toLinkCandidate);
		addEdge(fromPseudoStop, toPseudoStop, pathTravelCost, links, false);
	}

	private void addEdge(PseudoRouteStop from, PseudoRouteStop to, double edgeWeight, List<Link> networkLinks, boolean dummy) {
		if(!graph.containsKey(from.getId())) {
			graph.put(from.getId(), from);
		}
		if(!graph.containsKey(to.getId())) {
			graph.put(to.getId(), to);
		}
		graph.get(from.getId()).getNeighbours().put(graph.get(to.getId()), edgeWeight);

		// store links
		if(!dummy) {
			List<Link> links = new ArrayList<>();
			if(networkLinks == null) {
				ArtificialLink artificialLink = new ArtificialLinkImpl(from.getLinkCandidate(), to.getLinkCandidate(), 1, CoordUtils.calcEuclideanDistance(from.getLinkCandidate().getFromCoord(), to.getLinkCandidate().getToCoord()));
				links.add(artificialLink);
			} else {
				links.addAll(networkLinks);
			}
			stopPairLinks.put(getKey(from, to), links);
		}
	}

	/**
	 * @return String key to store links between stops
	 */
	private String getKey(PseudoRouteStop stopA, PseudoRouteStop stopB) {
		return stopA.getId() + "->" + stopB.getId();
	}
}


