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

import com.opencsv.CSVReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.gtfs.lib.GTFSDefinitions;
import org.matsim.pt2matsim.gtfs.lib.GtfsShape;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.matsim.pt2matsim.tools.CsvTools.getIndices;

/**
 * Wrapper class for a transit schedule that contains shapes
 *
 * @author polettif
 */
public class ShapedSchedule implements ShapedTransitSchedule {

	private final TransitSchedule schedule;

	private Map<Id<TransitLine>, Map<Id<TransitRoute>, Id<RouteShape>>> routeShapeRef = new HashMap<>();
	private Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();

	public ShapedSchedule() {
		this.schedule = ScheduleTools.createSchedule();
	}

	public ShapedSchedule(TransitSchedule transitSchedule) {
		this.schedule = transitSchedule;
	}

	@Override
	public void addShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, RouteShape shape) {
		shapes.put(shape.getId(), shape);
		MapUtils.getMap(transitLineId, routeShapeRef).put(transitRouteId, shape.getId());
	}

	@Override
	public RouteShape getShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		return shapes.get(routeShapeRef.get(transitLineId).get(transitRouteId));
	}

	@Override
	public Map<Id<TransitLine>, Map<Id<TransitRoute>, Id<RouteShape>>> getRouteShapeReference() {
		return routeShapeRef;
	}

	@Override
	public void readShapesFile(String shapesFilename, String outputCoordinateSystem) {
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem);

		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(shapesFilename));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GTFSDefinitions.Files.SHAPES.columns);
			String[] line = reader.readNext();
			while(line != null) {
				Id<RouteShape> shapeId = Id.create(line[col.get(GTFSDefinitions.SHAPE_ID)], RouteShape.class);
				RouteShape currentShape = shapes.get(shapeId);
				if(currentShape == null) {
					currentShape = new GtfsShape(line[col.get(GTFSDefinitions.SHAPE_ID)]);
					shapes.put(shapeId, currentShape);
				}
				Coord point = new Coord(Double.parseDouble(line[col.get(GTFSDefinitions.SHAPE_PT_LON)]), Double.parseDouble(line[col.get(GTFSDefinitions.SHAPE_PT_LAT)]));
				currentShape.addPoint(ct.transform(point), Integer.parseInt(line[col.get(GTFSDefinitions.SHAPE_PT_SEQUENCE)]));
				line = reader.readNext();
			}
			reader.close();
		} catch (IOException e) {
			throw new RuntimeException("File not found!");
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found file!");
		}
	}

	@Override
	public void readRouteShapeReferenceFile(String routeShapeRefFile) {
		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(routeShapeRefFile));
//			String[] header = reader.readNext(); // todo implement header in write method
			String[] line = reader.readNext();
			// 0 transitLineId
			// 1 transitRouteId
			// 2 shapeId
			while(line != null) {
				Id<TransitLine> transitLineId = Id.create(line[0], TransitLine.class);
				Id<TransitRoute> transitRouteId = Id.create(line[1], TransitRoute.class);
				Id<RouteShape> shapeId = Id.create(line[2], RouteShape.class);

				MapUtils.getMap(transitLineId, routeShapeRef).put(transitRouteId, shapeId); // add shape id to transit route id
				line = reader.readNext();
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

//		RouteShape currentShape = shapes.get(shapeId);
//		currentShape.addTransitRoute(transitLineId, transitRouteId); // add transit route id to shape
	}

	// TransitSchedule methods

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

	/**
	 * Writes the shapeSchedule to a (bloated) csv file
	 *
	 * @param filename
	 */
	@Deprecated
	private void writeShapeScheduleFile(String filename) {
		Map<Tuple<Integer, Integer>, String> keyTable = new HashMap<>();
		keyTable.put(new Tuple<>(1, 1), "shape_id");
		keyTable.put(new Tuple<>(1, 2), "shape_pt_lon");
		keyTable.put(new Tuple<>(1, 3), "shape_pt_lat");
		keyTable.put(new Tuple<>(1, 4), "shape_pt_sequence");
		keyTable.put(new Tuple<>(1, 5), "transitLineId");
		keyTable.put(new Tuple<>(1, 6), "transitRouteId");
/*
		int line = 2;
		for(Map.Entry<Id<TransitLine>, Map<Id<TransitRoute>, RouteShape>> transitLineEntry : routeShapeRef.entrySet()) {
			for(Map.Entry<Id<TransitRoute>, RouteShape> transitRouteEntry : transitLineEntry.getValue().entrySet()) {
				RouteShape shape = transitRouteEntry.getValue();
				for(Map.Entry<Integer, Coord> point : transitRouteEntry.getValue().getPoints().entrySet()) {
					keyTable.put(new Tuple<>(line, 1), shape.getId().toString());
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
		} */
	}


	/**
	 * reads a shapes from a file
	 */
	@Deprecated
	private void readShapeScheduleFile(String filename) {
		Map<Id<RouteShape>, RouteShape> tmp = new HashMap<>();
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
				Id<RouteShape> shapeId = Id.create(line[0], RouteShape.class);
				Id<TransitLine> transitLineId = Id.create(line[4], TransitLine.class);
				Id<TransitRoute> transitRouteId = Id.create(line[5], TransitRoute.class);

				RouteShape currentShape = tmp.get(shapeId);
				if(currentShape == null) {
					currentShape = new GtfsShape(shapeId);
					currentShape.addTransitRoute(transitLineId, transitRouteId);
					shapes.put(shapeId, currentShape);
				}
				Coord point = new Coord(Double.parseDouble(line[1]), Double.parseDouble(line[2]));
				currentShape.addPoint(point, Integer.parseInt(line[3]));

				currentShape.addTransitRoute(transitLineId, transitRouteId);
//				MapUtils.getMap(transitLineId, tmp).put(transitRouteId, currentShape);

				line = reader.readNext();
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
