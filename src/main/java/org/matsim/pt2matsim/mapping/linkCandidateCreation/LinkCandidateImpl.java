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
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt2matsim.tools.CoordTools;

/**
 * @author polettif
 */
public class LinkCandidateImpl implements LinkCandidate {

	private final Link link;
	private final PublicTransitStop stop;

	// helper fields
	private final double stopFacilityDistance;
	private final Coord fromNodeCoord;
	private final Coord toNodeCoord;
	private final boolean isLoopLink;
	private double priority;
	private boolean geometryAware;
	private boolean rightSide;

	public LinkCandidateImpl(Link link, PublicTransitStop publicTransitStop) {
		this(link, publicTransitStop, CoordUtils.distancePointLinesegment(link.getFromNode().getCoord(),
				link.getToNode().getCoord(), publicTransitStop.getStopFacility().getCoord()), false);
		this.geometryAware = false;
	}

	public LinkCandidateImpl(Link link, PublicTransitStop publicTransitStop, double distance, boolean rightSide) {
		this.link = link;
		this.stop = publicTransitStop;

		this.fromNodeCoord = link.getFromNode().getCoord();
		this.toNodeCoord = link.getToNode().getCoord();
		this.stopFacilityDistance = distance;
		this.isLoopLink = link.getFromNode().getId().toString().equals(link.getToNode().getId().toString());
		this.priority = 1;
		this.geometryAware = true;
		this.rightSide = rightSide;
	}

	@Override
	public double getStopFacilityDistance() {
		return stopFacilityDistance;
	}

	@Override
	public double getPriority() {
		return priority;
	}

	@Override
	public void setPriority(double priority) {
		this.priority = priority;
	}

	@Override
	public PublicTransitStop getStop() {
		return stop;
	}

	@Override
	public Link getLink() {
		return link;
	}

	@Override
	public Coord getToCoord() {
		return fromNodeCoord;
	}

	@Override
	public Coord getFromCoord() {
		return toNodeCoord;
	}

	@Override
	public String toString() {
		return "[LinkCandidate link=" + link.getId() + " stop=" + stop + "]";
	}

	@Override
	public boolean isLoopLink() {
		return isLoopLink;
	}

	@Override
	public int compareTo(LinkCandidate other) {
		if(this.equals(other)) {
			return 0;
		} else {
			if(priority == other.getPriority()) {
				if (geometryAware && other instanceof LinkCandidateImpl candidate && candidate.geometryAware) {
					int side = Boolean.compare(candidate.rightSide, rightSide);
					return side != 0 ? side : link.getId().toString().compareTo(candidate.link.getId().toString());
				}
				return CoordTools.coordIsOnRightSideOfLine(stop.getStopFacility().getCoord(), fromNodeCoord, toNodeCoord) ? -1 : 1;
			} else {
				return priority > other.getPriority() ? -1 : 1;
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		LinkCandidateImpl that = (LinkCandidateImpl) o;

		if(Double.compare(that.priority, priority) != 0) return false;
		if(!link.equals(that.link)) return false;
		return stop.equals(that.stop);
	}

	@Override
	public int hashCode() {
		int result;
		long temp;
		result = link.hashCode();
		result = 31 * result + stop.hashCode();
		temp = Double.doubleToLongBits(priority);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}
}
