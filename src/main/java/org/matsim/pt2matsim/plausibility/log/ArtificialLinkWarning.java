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

package org.matsim.pt2matsim.plausibility.log;

import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.ArrayList;

/**
 * Plausibility warning if a link is an artificial link (i.e. created by the pt mapper).
 *
 * @author polettif
 */
public class ArtificialLinkWarning extends AbstractPlausibilityWarning {

	public ArtificialLinkWarning(TransitLine transitLine, TransitRoute transitRoute, Link link) {
		super(Type.ArtificialLinkWarning, transitLine, transitRoute);
		this.fromId = link.getFromNode().getId().toString();
		this.toId = link.getToNode().getId().toString();

		linkIdList = new ArrayList<>();
		linkIdList.add(link.getId());
	}

	@Override
	public String toString() {
		return "[ArtificialLink, link:" + linkIdList.get(0) +"]";
	}
}
