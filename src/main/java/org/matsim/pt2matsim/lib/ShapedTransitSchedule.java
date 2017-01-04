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

package org.matsim.pt2matsim.lib;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.util.Collection;
import java.util.Map;

/**
 * Wrapper class for a transit schedule that contains
 * routeShapes for transitRoutes. Is used for {@link org.matsim.pt2matsim.mapping.PTMapperWithShapes} and
 * {@link org.matsim.pt2matsim.tools.MappingAnalysis}
 *
 * @author polettif
 */
public interface ShapedTransitSchedule extends TransitSchedule {

	void addShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, RouteShape shape);

	RouteShape getShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId);

	Map<Id<RouteShape>, ? extends RouteShape> getShapes();

	/**
	 * Reads a gtfs formatted shapes file.
	 *
	 * @param shapesFilename normally called shapes.txt
	 * @param outputCoordinateSystem output coordinate system, set to <tt>null</tt> if no transformation should be applied
	 */
	void readShapesFile(String shapesFilename, String outputCoordinateSystem);

	/**
	 * Reads a route shape reference file, a csv with three columns and a header
	 * <tt>transitLineId, transitRouteId, shapeId</tt>
	 */
	void readRouteShapeReferenceFile(String routeShapeRefFile);

	TransitRouteShapeReference getTransitRouteShapeReference();

	void setTransitRouteShapeReference(TransitRouteShapeReference ref);

}
