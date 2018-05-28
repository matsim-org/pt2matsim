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

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.List;

/**
 * Plausibility warning if a link sequence passes a node twice.
 *
 * @author polettif
 */
public class LoopWarning extends AbstractPlausibilityWarning {

	private Node node;

	public LoopWarning(TransitLine transitLine, TransitRoute transitRoute, Node node, Link firstLoopLink, Link lastLoopLink) {
		super(Type.LoopWarning, transitLine, transitRoute);
		this.node = node;

		linkIdList = ScheduleTools.getLoopSubRouteLinkIds(transitRoute, firstLoopLink.getId(), lastLoopLink.getId());

		fromId = firstLoopLink.getId().toString();
		toId = lastLoopLink.getId().toString();
	}

	public LoopWarning(TransitLine transitLine, TransitRoute transitRoute, List<Id<Link>> loop) {
		super(Type.LoopWarning, transitLine, transitRoute);

		linkIdList = loop;

		fromId = loop.get(0).toString();
		toId = loop.get(loop.size()-1).toString();
		expected = 0;
		actual = 0;
	}

	@Override
	public String toString() {
		return "[Loop, node:"+node.getId()+"]";
	}
}
