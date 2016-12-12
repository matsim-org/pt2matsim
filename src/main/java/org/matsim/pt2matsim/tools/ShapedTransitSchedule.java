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

package org.matsim.pt2matsim.tools;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * Wrapper class for a transit schedule that contains shapes
 *
 * @author polettif
 */
public interface ShapedTransitSchedule extends TransitSchedule {

	void addShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, RouteShape shape);

	RouteShape getShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId);

	/**
	 * Reads a gtfs formatted shapes file.
	 *
	 * @param shapesFilename normally called shapes.txt
	 * @param outputCoordinateSystem output coordinate system, set to <tt>null</tt> if no transformation should be applied
	 */
	void readShapesFile(String shapesFilename, String outputCoordinateSystem);

	void readRouteShapeReferenceFile(String routeShapeRefFile);

	void writeRouteShapeReferenceFile(String routeShapeRefFile);
}
