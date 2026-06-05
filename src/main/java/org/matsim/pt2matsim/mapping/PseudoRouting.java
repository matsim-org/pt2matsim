package org.matsim.pt2matsim.mapping;

import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.mapping.pseudoRouter.ArtificialLink;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoGraph;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoSchedule;
import org.matsim.pt2matsim.mapping.pseudoRouter.PseudoTransitRoute;

/**
 * Generates and calculates the {@link PseudoTransitRoute} for each queued
 * {@link TransitRoute} using a {@link PseudoGraph}. Stores the PseudoTransitRoutes
 * in a {@link PseudoSchedule}.<p/>
 *
 * If no path on the network can be found, an {@link ArtificialLink} between
 * {@link LinkCandidate}s can be added to a network.
 *
 * @author polettif
 */
public interface PseudoRouting extends Runnable {

	/**
	 * Adds a single transit route (within its line) to the queue processed in {@link #run()}.
	 * <p>
	 * This is the unit-of-work entry point and should be preferred over
	 * {@link #addTransitLineToQueue(TransitLine)} so that idle workers can pick up
	 * remaining routes from busy workers (dynamic load balancing).
	 */
	void addTransitRouteToQueue(TransitLine line, TransitRoute route);

	/**
	 * Adds all routes of a transit line to the queue.
	 * <p>
	 * Provided for backward compatibility. Internally enqueues each {@link TransitRoute}
	 * via {@link #addTransitRouteToQueue(TransitLine, TransitRoute)}.
	 */
	default void addTransitLineToQueue(TransitLine transitLine) {
		for (TransitRoute r : transitLine.getRoutes().values()) {
			addTransitRouteToQueue(transitLine, r);
		}
	}

	/**
	 * Executes the PseudoRouting algorithm
	 */
	void run();

	/**
	 * @return a PseudoSchedule that contains all PseudoRoute for the queued lines
	 */
	PseudoSchedule getPseudoSchedule();

	/**
	 * Adds the necessary artificial links to the network.
	 */
	void addArtificialLinks(Network network);

}
