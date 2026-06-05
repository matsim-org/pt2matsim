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

package org.matsim.pt2matsim.mapping;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreator;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersFactory;
import org.matsim.pt2matsim.mapping.pseudoRouter.*;

import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Generates and calculates the pseudoRoutes for all the queued
 * transit routes. If no route on the network can be found (or the
 * scheduleTransportMode should not be mapped to the network), artificial
 * links between link candidates are stored to be created later.
 * <p>
 * The unit of work is a single {@link TransitRoute}. When several worker
 * instances share the same {@link Queue}, idle workers automatically pick
 * up remaining routes from busy workers, eliminating the "stuck at 99 %"
 * long-tail behaviour caused by line-level pre-assignment.
 *
 * @author polettif
 */
public class PseudoRoutingImpl implements PseudoRouting {

	protected static Logger log = LogManager.getLogger(PseudoRoutingImpl.class);
	private final Progress progress;

	private static boolean warnMinTravelCost = true;

	/** Element of the work queue: the route to map plus its owning line. */
	public record QueuedRoute(TransitLine line, TransitRoute route) {}

	private final Queue<QueuedRoute> queue;

	private final LinkCandidateCreator linkCandidates;
	private final ScheduleRouters scheduleRouters;

	private final Set<ArtificialLink> necessaryArtificialLinks = new HashSet<>();

	private final PseudoSchedule threadPseudoSchedule = new PseudoScheduleImpl();
	private final double maxTravelCostFactor;

	/**
	 * Backward-compatible constructor: the runnable owns a private queue.
	 * Prefer the shared-queue overload below so multiple workers can balance load dynamically.
	 */
	public PseudoRoutingImpl(ScheduleRoutersFactory scheduleRoutersFactory, LinkCandidateCreator linkCandidates,
			double maxTravelCostFactor, Progress progress) {
		this(scheduleRoutersFactory, linkCandidates, maxTravelCostFactor, progress,
				new ConcurrentLinkedQueue<>());
	}

	/**
	 * Shared-queue constructor. All workers given the same {@code sharedQueue} poll dynamically,
	 * so a thread that finishes early picks up routes pending on slower threads.
	 */
	public PseudoRoutingImpl(ScheduleRoutersFactory scheduleRoutersFactory, LinkCandidateCreator linkCandidates,
			double maxTravelCostFactor, Progress progress, Queue<QueuedRoute> sharedQueue) {
		this.maxTravelCostFactor = maxTravelCostFactor;
		this.scheduleRouters = scheduleRoutersFactory.createInstance();
		this.linkCandidates = linkCandidates;
		this.progress = progress;
		this.queue = sharedQueue;
	}

	@Override
	public void addTransitRouteToQueue(TransitLine line, TransitRoute route) {
		queue.add(new QueuedRoute(line, route));
	}

	@Override
	public void run() {
		QueuedRoute qr;
		while ((qr = queue.poll()) != null) {
			processRoute(qr.line(), qr.route());
			progress.update();
		}
	}

	/**
	 * Per-route processing.
	 *
	 * <p>The body below is intentionally identical to the previous in-line implementation;
	 * only the surrounding {@code for (TransitRoute ...)} loop has moved out.</p>
	 */
	private void processRoute(TransitLine transitLine, TransitRoute transitRoute) {
		/* [1]
		  Initiate pseudoGraph and Dijkstra algorithm for the current transitRoute.

		  In the pseudoGraph, all link candidates are represented as nodes and the
		  network paths between link candidates are reduced to a representation edge
		  only storing the travel cost. With the pseudoGraph, the best linkCandidate
		  sequence can be calculated (using Dijkstra). From this sequence, the actual
		  path on the network can be routed later on.
		 */
		PseudoGraph pseudoGraph = new PseudoGraphImpl();

		/* [2]
		  Calculate the shortest paths between each pair of routeStops/ParentStopFacility
		 */
		List<TransitRouteStop> routeStops = transitRoute.getStops();
		for(int i = 0; i < routeStops.size() - 1; i++) {
			Set<LinkCandidate> linkCandidatesCurrent = linkCandidates.getLinkCandidates(routeStops.get(i), transitLine, transitRoute);
			Set<LinkCandidate> linkCandidatesNext = linkCandidates.getLinkCandidates(routeStops.get(i + 1), transitLine, transitRoute);

			double minTravelCost = scheduleRouters.getMinimalTravelCost(routeStops.get(i), routeStops.get(i + 1), transitLine, transitRoute);
			double maxAllowedTravelCost = minTravelCost * maxTravelCostFactor;

			if(minTravelCost == 0 && warnMinTravelCost) {
				log.warn("There are stop pairs where minTravelCost is 0.0! This might happen if two stops are on the same coordinate or if departure and arrival time of two subsequent stops are identical. Further messages are suppressed.");
				warnMinTravelCost = false;
			}

			/* [3]
			  Calculate the shortest path between all link candidates.
			 */
			for(LinkCandidate linkCandidateCurrent : linkCandidatesCurrent) {
				for(LinkCandidate linkCandidateNext : linkCandidatesNext) {

					boolean useExistingNetworkLinks = false;
					double pathCost = 2 * maxAllowedTravelCost;
					List<Link> pathLinks = null;

					/* [3.1]
					  If one or both link candidates are loop links we don't have
					  to search a least cost path on the network.
					 */
					if(!linkCandidateCurrent.isLoopLink() && !linkCandidateNext.isLoopLink()) {
						/*
						  Calculate the least cost path on the network
						 */
						LeastCostPathCalculator.Path leastCostPath = scheduleRouters.calcLeastCostPath(linkCandidateCurrent, linkCandidateNext, transitLine, transitRoute);

						if(leastCostPath != null) {
							pathCost = leastCostPath.travelCost;
							pathLinks = leastCostPath.links;
							// if both link candidates are the same, cost should get higher
							if(linkCandidateCurrent.getLink().getId().equals(linkCandidateNext.getLink().getId())) {
								pathCost *= 4;
							}
						}
						useExistingNetworkLinks = pathCost < maxAllowedTravelCost;
					}

					/* [3.2]
					  If a path on the network could be found and its travel cost are
					  below maxAllowedTravelCost, a normal edge is added to the pseudoGraph
					 */
					if(useExistingNetworkLinks) {
						double currentCandidateTravelCost = scheduleRouters.getLinkCandidateTravelCost(linkCandidateCurrent);
						double nextCandidateTravelCost = scheduleRouters.getLinkCandidateTravelCost(linkCandidateNext);
						double edgeWeight = pathCost + 0.5 * currentCandidateTravelCost + 0.5 * nextCandidateTravelCost;

						pseudoGraph.addEdge(i, routeStops.get(i), linkCandidateCurrent, routeStops.get(i + 1), linkCandidateNext, edgeWeight, pathLinks);
					}
					/* [3.2]
					  Create artificial links between two routeStops if:
					  	 - no path on the network could be found
					    - the travel cost of the path are greater than maxAllowedTravelCost

					  Artificial links are created between all LinkCandidates
					  (usually this means between one dummy link for the stop
					  facility and the other linkCandidates).
					 */
					else {
						double currentCandidateTravelCost = scheduleRouters.getLinkCandidateTravelCost(linkCandidateCurrent);
						double nextCandidateTravelCost = scheduleRouters.getLinkCandidateTravelCost(linkCandidateNext);
						double artificialEdgeWeight = maxAllowedTravelCost - 0.5 * currentCandidateTravelCost - 0.5 * nextCandidateTravelCost;

						pseudoGraph.addEdge(i, routeStops.get(i), linkCandidateCurrent, routeStops.get(i + 1), linkCandidateNext, artificialEdgeWeight, null);
					}
				}
			}
		} // - routeStop loop

		/* [4]
		  Finish the pseudoGraph by adding dummy nodes.
		 */
		pseudoGraph.addDummyEdges(routeStops,
				linkCandidates.getLinkCandidates(routeStops.get(0), transitLine, transitRoute),
				linkCandidates.getLinkCandidates(routeStops.get(routeStops.size() - 1), transitLine, transitRoute));

		/* [5]
		  Find the least cost path i.e. the PseudoRouteStop sequence
		 */
		List<PseudoRouteStop> pseudoPath = pseudoGraph.getLeastCostStopSequence();

		if(pseudoPath == null) {
			throw new RuntimeException("PseudoGraph has no path from SOURCE to DESTINATION for transit route " + transitRoute.getId() + " " +
					"on line " + transitLine.getId() + " from \"" + routeStops.get(0).getStopFacility().getName() + "\" " +
					"to \"" + routeStops.get(routeStops.size() - 1).getStopFacility().getName() + "\"");
		} else {
			necessaryArtificialLinks.addAll(pseudoGraph.getArtificialNetworkLinks());
			threadPseudoSchedule.addPseudoRoute(transitLine, transitRoute, pseudoPath, pseudoGraph.getNetworkLinkIds());
		}
	}


	/**
	 * @return a pseudo schedule generated during run()
	 */
	@Override
	public PseudoSchedule getPseudoSchedule() {
		return threadPseudoSchedule;
	}

	/**
	 * Adds the artificial links to the network.
	 *
	 * Not thread safe.
	 */
	@Override
	public void addArtificialLinks(Network network) {
		for(ArtificialLink a : necessaryArtificialLinks) {
			if(!network.getLinks().containsKey(a.getId())) {
				network.addLink(a);
			}
		}
	}

}
