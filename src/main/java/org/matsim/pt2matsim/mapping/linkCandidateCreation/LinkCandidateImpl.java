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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.CoordTools;

/**
 * @author polettif
 */
public class LinkCandidateImpl implements LinkCandidate {

	private final String id;
	private final Id<TransitStopFacility> parentStopFacilityId;
	private final Link link;
	private final double stopFacilityDistance;
	private final Id<Link> linkId;
	private final Id<Node> fromNodeId;
	private final Id<Node> toNodeId;
	private final Coord stopFacilityCoord;
	private final Coord fromNodeCoord;
	private final Coord toNodeCoord;
	private final boolean loopLink;
	private double priority;

	public LinkCandidateImpl(Link link, TransitStopFacility parentStopFacility) {
		this.id = parentStopFacility.getId().toString() + ".link:" + link.getId().toString();
		this.parentStopFacilityId = parentStopFacility.getId();

		this.linkId = link.getId();

		this.fromNodeId = link.getFromNode().getId();
		this.toNodeId = link.getToNode().getId();
		this.stopFacilityCoord = parentStopFacility.getCoord();

		this.fromNodeCoord = link.getFromNode().getCoord();
		this.toNodeCoord = link.getToNode().getCoord();

		this.stopFacilityDistance = CoordUtils.distancePointLinesegment(fromNodeCoord, toNodeCoord, stopFacilityCoord);
		this.priority = 1/stopFacilityDistance;

		this.loopLink = link.getFromNode().getId().toString().equals(link.getToNode().getId().toString());

		this.link = link;
	}

	public LinkCandidateImpl() {
		this.id = "dummy";
		this.parentStopFacilityId = null;

		this.linkId = null;

		this.fromNodeId = null;
		this.toNodeId = null;
		this.stopFacilityCoord = null;

		this.fromNodeCoord = null;
		this.toNodeCoord = null;

		this.stopFacilityDistance = 0.0;
		this.priority = 0.0;

		this.loopLink = true;

		this.link = null;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Id<TransitStopFacility> getStopFacilityId() {
		return parentStopFacilityId;
	}

	@Override
	public Id<Link> getLinkId() {
		return linkId;
	}

	@Override
	public Id<Node> getToNodeId() {
		return toNodeId;
	}

	@Override
	public Id<Node> getFromNodeId() {
		return fromNodeId;
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
	public Link getLink() {
		return link;
	}

	@Override
	public Coord getFromNodeCoord() {
		return fromNodeCoord;
	}

	@Override
	public Coord getToNodeCoord() {
		return toNodeCoord;
	}

	@Override
	public String toString() {
		return id;
	}

	@Override
	public boolean isLoopLink() {
		return loopLink;
	}

	@Override
	public int compareTo(LinkCandidate other) {
		if(this.equals(other)) {
			return 0;
		}

		if(other instanceof LinkCandidateImpl) {
			LinkCandidateImpl o = (LinkCandidateImpl) other;
			int dCompare = Double.compare(stopFacilityDistance, o.getStopFacilityDistance());
			if(dCompare == 0) {
				return CoordTools.coordIsOnRightSideOfLine(stopFacilityCoord, fromNodeCoord, toNodeCoord) ? 1 : -1;
			} else {
				return dCompare;
			}
		} else {
			int dCompare = -Double.compare(priority, other.getPriority());
			return dCompare == 0 ? 1 : dCompare;
		}
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		LinkCandidateImpl that = (LinkCandidateImpl) o;
		return getId().equals(that.getId());
	}

	@Override
	public int hashCode() {
		return getId().hashCode();
	}
}