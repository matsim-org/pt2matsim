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
 * Plausibility warning if a link sequence has abrupt direction changes.
 *
 * @author polettif
 */
public class DirectionChangeWarning extends AbstractPlausibilityWarning {

	public DirectionChangeWarning(TransitLine transitLine, TransitRoute transitRoute, Link link1, Link link2, double angleDiff) {
		super(Type.DirectionChangeWarning, transitLine, transitRoute);
		this.fromId = link1.getId().toString();
		this.toId = link2.getId().toString();

		difference = angleDiff;

		linkIdList = new ArrayList<>();
		linkIdList.add(link1.getId());
		linkIdList.add(link2.getId());
	}

	@Override
	public String toString() {
		return String.format("[DirectionChange, fromLink:%s, toLink:%s, diff:%.1f°]", fromId, toId, difference*180/Math.PI);
	}
}
