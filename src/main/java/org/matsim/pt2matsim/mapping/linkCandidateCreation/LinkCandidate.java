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

package org.matsim.pt2matsim.mapping.linkCandidateCreation;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;

/**
 * A possible link for a {@link PublicTransitStop}. A LinkCandidate contains
 * theoretically a link and the parent StopFacility. However, all
 * values besides Coord should be stored as primitive/Id since one might
 * be working with multiple separated networks.
 *
 * @author polettif
 */
public interface LinkCandidate extends Comparable<LinkCandidate> {

	Link getLink();

	PublicTransitStop getStop();

	Coord getFromCoord();

	Coord getToCoord();

	double getStopFacilityDistance();

	/**
	 * @return true if the link candidate is an artificial loop link
	 */
	boolean isLoopLink();

	/**
	 * Should return a value greater than 1 if the other LinkCandidate
	 * has a lower priority.
	 */
	int compareTo(LinkCandidate other);

	/**
	 * @return the link candidates priority compared to all other
	 * link candidates for the same stop and transit route. The priority
	 * is scaled 0..1 (1 being high priority).
	 */
	double getPriority();

	void setPriority(double priority);
}
