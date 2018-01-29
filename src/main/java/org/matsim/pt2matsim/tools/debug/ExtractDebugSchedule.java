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

package org.matsim.pt2matsim.tools.debug;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.pt2matsim.run.gis.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extract one route from a schedule
 *
 * @author polettif
 */
public class ExtractDebugSchedule {

	/**
	 * Extracts one route from a schedule and writes it
	 * to a new file.
	 *
	 * @param args [0] schedule file
	 *             [1] transit line id
	 *             [2] transit route id (use * for all transit routes of line)
	 *             [3] output debug schedule file
	 */
	public static void main(final String[] args) {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(args[0]);
		TransitSchedule debug = ScheduleTools.createSchedule();

		for(TransitLine tl : schedule.getTransitLines().values()) {
			if(tl.getId().toString().equals(args[1])) {
				TransitLine line = debug.getFactory().createTransitLine(tl.getId());
				for(TransitRoute tr : tl.getRoutes().values()) {
					if(tr.getId().toString().equals(args[2]) || args[2].equals("*")) {
						line.addRoute(tr);

						for(TransitRouteStop rs : tr.getStops()) {
							if(!debug.getFacilities().containsKey(rs.getStopFacility().getId())) {
								debug.addStopFacility(rs.getStopFacility());
							}
						}
					}
				}
				debug.addTransitLine(line);
			}
		}
		ScheduleTools.writeTransitSchedule(debug, args[3]);
	}

	public static void run(TransitSchedule schedule, String transitLineId, String transitRouteId) {
		Set<TransitLine> toRemove = new HashSet<>();
		for(TransitLine tl : new HashSet<>(schedule.getTransitLines().values())) {
			if(tl.getId().toString().equals(transitLineId)) {
				for(TransitRoute tr : new HashSet<>(tl.getRoutes().values())) {
					if(!tr.getId().toString().equals(transitRouteId)) {
						tl.removeRoute(tr);
					}
				}
			} else {
				toRemove.add(tl);
			}
		}

		for(TransitLine tl : toRemove) {
			schedule.removeTransitLine(tl);
		}
	}

	/**
	 * Extracts n transit routes randomly. Removes all other transit routes
	 * from the schedule
	 */
	public static void removeRand(TransitSchedule schedule, int n) {
		int nRoutes = 0;
		for(TransitLine tl : schedule.getTransitLines().values()) {
			nRoutes += tl.getRoutes().size();
		}

		double p = n / (double) nRoutes;

		for(TransitLine tl : schedule.getTransitLines().values()) {
			for(TransitRoute tr : new HashSet<>(tl.getRoutes().values())) {
				double t = Math.random();
				if(t > p) {
					tl.removeRoute(tr);
				}
			}
		}

		for(TransitLine tl : new HashSet<>(schedule.getTransitLines().values())) {
			if(tl.getRoutes().size() == 0) {
				schedule.removeTransitLine(tl);
			}
		}
	}

	public static void writeRouteAndShapeToShapefile(TransitSchedule schedule, Network network, Map<Id<RouteShape>, RouteShape> shapes, String outputFolder, String debugLineId, String debugRouteId, String coordSys) {
		ExtractDebugSchedule.run(schedule, debugLineId, debugRouteId);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		Schedule2ShapeFile s2s = new Schedule2ShapeFile(coordSys, schedule, network);
		s2s.routes2Polylines(outputFolder + "transitRoutes.shp", true);
		s2s.routes2Polylines(outputFolder + "transitRoutesBeeline.shp", false);
		s2s.stopFacilities2Points(outputFolder + "stopFacilities.shp");

		Id<RouteShape> shapeId = ScheduleTools.getShapeId(schedule.getTransitLines().get(Id.create(debugLineId, TransitLine.class)).getRoutes().get(Id.create(debugRouteId, TransitRoute.class)));
		ShapeTools.writeESRIShapeFile(Collections.singleton(shapes.get(shapeId)), coordSys, outputFolder+"shape.shp");

	}

}