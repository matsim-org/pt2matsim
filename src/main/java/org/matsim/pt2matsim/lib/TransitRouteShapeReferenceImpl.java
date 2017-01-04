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
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.tools.CsvTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author polettif
 */
public class TransitRouteShapeReferenceImpl implements TransitRouteShapeReference {


	private Map<Id<TransitLine>, Map<Id<TransitRoute>, Id<RouteShape>>> routeShapeRefMap = new HashMap<>();

	public TransitRouteShapeReferenceImpl() {

	}

	public TransitRouteShapeReferenceImpl(String shapeRefFile) {
		readFile(shapeRefFile);
	}


	@Override

	public void setShapeId(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, Id<RouteShape> shapeId) {
		MapUtils.getMap(transitLineId, routeShapeRefMap).put(transitRouteId, shapeId);
	}

	/**
	 * @return null if no shpae id is defined
	 */
	@Override
	public Id<RouteShape> getShapeId(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		Map<Id<TransitRoute>, Id<RouteShape>> lineMap = routeShapeRefMap.get(transitLineId);
		if(lineMap == null) {
			return null;
		}
		return lineMap.get(transitRouteId);
	}

	@Override
	public void writeToFile(String filename) {
		String[] header = new String[3];
		header[0] = "transitLineId";
		header[1] = "transitRouteId";
		header[2] = "shapeId";
		try {
			CsvTools.writeNestedMapToFile(header, routeShapeRefMap, filename);
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not write references to file.");
		}
	}

	@Override
	public void readFile(String routeShapeRefFile) {
		Map<String, Map<String, String>> stringMap;
		try {
			stringMap = CsvTools.readNestedMapFromFile(routeShapeRefFile, true);
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not read references from file.");
		}

		for(Map.Entry<String, Map<String, String>> e : stringMap.entrySet()) {
			for(Map.Entry<String, String> f : e.getValue().entrySet()) {
				Id<TransitLine> transitLineId = Id.create(e.getKey(), TransitLine.class);
				Id<TransitRoute> transitRouteId = Id.create(f.getKey(), TransitRoute.class);
				Id<RouteShape> shapeId = Id.create(f.getValue(), RouteShape.class);
				MapUtils.getMap(transitLineId, routeShapeRefMap).put(transitRouteId, shapeId); // add shape id to transit route id
			}
		}
	}


}
