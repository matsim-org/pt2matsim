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

import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * @author polettif
 */
public class LinkCandidateMode extends LinkCandidateImpl {

	private final String id;
	private final String mode;

	public LinkCandidateMode(Link link, TransitStopFacility parentStopFacility, double linkTravelCost, String mode) {
		super(link, parentStopFacility, linkTravelCost);
		this.id = parentStopFacility.getId().toString() + ".mode:" + mode + ".link:" + link.getId().toString();
		this.mode = mode;
	}

	public LinkCandidateMode() {
		super();
		this.id = "dummy";
		this.mode = null;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		LinkCandidate other = (LinkCandidate) obj;
		if(id == null) {
			if(other.getId() != null)
				return false;
		} else if(!id.equals(other.getId()))
			return false;
		return true;
	}

	public int hashCode() {
		return id.hashCode();
	}
}