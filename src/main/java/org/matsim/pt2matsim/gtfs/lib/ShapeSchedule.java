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

package org.matsim.pt2matsim.gtfs.lib;

import com.opencsv.CSVReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.CsvTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for a transit schedule that contains GTFS shapes
 *
 * @author polettif
 */
public class ShapeSchedule implements TransitSchedule {

	private final TransitSchedule schedule;
	private Map<Id<TransitLine>, Map<Id<TransitRoute>, Shape>> refShapes = new HashMap<>();
	private Map<String, Shape> shapes = new HashMap<>();

	public ShapeSchedule() {
		this.schedule = ScheduleTools.createSchedule();
	}

	public ShapeSchedule(TransitSchedule transitSchedule) {
		this.schedule = transitSchedule;
	}

	public ShapeSchedule(String transitScheduleFile, String shapeRefFile) {
		this.schedule = ScheduleTools.readTransitSchedule(transitScheduleFile);
		readShapeScheduleFile(shapeRefFile);
	}

	public void addShape(Shape shape) {
		for(Tuple<Id<TransitLine>, Id<TransitRoute>> tltr : shape.getTransitRoutes()) {
			MapUtils.getMap(tltr.getFirst(), refShapes).put(tltr.getSecond(), shape);
		}
		this.shapes.put(shape.getId(), shape);
	}

	public Shape getShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		return refShapes.get(transitLineId).get(transitRouteId);
	}


	/**
	 * Writes the shapeSchedule to a (bloated) csv file
	 * @param filename
	 */
	public void writeShapeScheduleFile(String filename) {
		Map<Tuple<Integer, Integer>, String> keyTable = new HashMap<>();
		keyTable.put(new Tuple<>(1, 1), "shape_id");
		keyTable.put(new Tuple<>(1, 2), "shape_pt_lon");
		keyTable.put(new Tuple<>(1, 3), "shape_pt_lat");
		keyTable.put(new Tuple<>(1, 4), "shape_pt_sequence");
		keyTable.put(new Tuple<>(1, 5), "transitLineId");
		keyTable.put(new Tuple<>(1, 6), "transitRouteId");

		int line = 2;
		for(Map.Entry<Id<TransitLine>, Map<Id<TransitRoute>, Shape>> transitLineEntry : refShapes.entrySet()) {
			for(Map.Entry<Id<TransitRoute>, Shape> transitRouteEntry : transitLineEntry.getValue().entrySet()) {
				Shape shape = transitRouteEntry.getValue();
				for(Map.Entry<Integer, Coord> point : transitRouteEntry.getValue().getPoints().entrySet()) {
					keyTable.put(new Tuple<>(line, 1), shape.getId());
					keyTable.put(new Tuple<>(line, 2), Double.toString(point.getValue().getX()));
					keyTable.put(new Tuple<>(line, 3), Double.toString(point.getValue().getY()));
					keyTable.put(new Tuple<>(line, 4), Integer.toString((int) point.getKey()));
					keyTable.put(new Tuple<>(line, 5), transitLineEntry.getKey().toString());
					keyTable.put(new Tuple<>(line, 6), transitRouteEntry.getKey().toString());
					line++;
				}
			}
		}
		List<String> csvLines = CsvTools.convertToCsvLines(keyTable, ',');
		try {
			CsvTools.writeToFile(csvLines, filename);
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}


	/**
	 * reads a shapes from a file
	 */
	public void readShapeScheduleFile(String filename) {
		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(filename));
			String[] header = reader.readNext(); // read header

			String[] line = reader.readNext();

			// 0 shape_id
			// 1 shape_pt_lon
			// 2 shape_pt_lat
			// 3 shape_pt_sequence
			// 4 transitLineId
			// 5 transitRouteId
			while(line != null) {
				String shapeId = line[0];
				Id<TransitLine> transitLineId = Id.create(line[4], TransitLine.class);
				Id<TransitRoute> transitRouteId = Id.create(line[5], TransitRoute.class);

				Shape currentShape = shapes.get(shapeId);
				if(currentShape == null) {
					currentShape = new Shape(shapeId);
					currentShape.addTransitRoute(transitLineId, transitRouteId);
					shapes.put(shapeId, currentShape);
				}
				Coord point = new Coord(Double.parseDouble(line[1]), Double.parseDouble(line[2]));
				currentShape.addPoint(point, Integer.parseInt(line[3]));

				currentShape.addTransitRoute(transitLineId, transitRouteId);
				MapUtils.getMap(transitLineId, refShapes).put(transitRouteId, currentShape);

				line = reader.readNext();
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void addTransitLine(TransitLine transitLine) {
		this.schedule.addTransitLine(transitLine);
	}

	@Override
	public boolean removeTransitLine(TransitLine transitLine) {
		return this.schedule.removeTransitLine(transitLine);
	}

	@Override
	public void addStopFacility(TransitStopFacility transitStopFacility) {
		this.schedule.addStopFacility(transitStopFacility);
	}

	@Override
	public Map<Id<TransitLine>, TransitLine> getTransitLines() {
		return this.schedule.getTransitLines();
	}

	@Override
	public Map<Id<TransitStopFacility>, TransitStopFacility> getFacilities() {
		return this.schedule.getFacilities();
	}

	@Override
	public boolean removeStopFacility(TransitStopFacility transitStopFacility) {
		return this.schedule.removeStopFacility(transitStopFacility);
	}

	@Override
	public TransitScheduleFactory getFactory() {
		return this.schedule.getFactory();
	}

	@Override
	public ObjectAttributes getTransitLinesAttributes() {
		return this.schedule.getTransitLinesAttributes();
	}

	@Override
	public ObjectAttributes getTransitStopsAttributes() {
		return this.schedule.getTransitStopsAttributes();
	}

}
