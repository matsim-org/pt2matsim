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

import com.opencsv.CSVReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions;
import org.matsim.pt2matsim.gtfs.lib.GtfsShape;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.io.FileNotFoundException;
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

	private TransitRouteShapeReference transitRouteShapeRef = new TransitRouteShapeReferenceImpl();
	private Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();

	public ShapedSchedule(TransitSchedule transitSchedule) {
		this.schedule = transitSchedule;
	}

	public ShapedSchedule(String transitScheduleFile, String routeShapeRefFile, String shapesFile, String gtfsOutputCoordSystem) {
		this.schedule = ScheduleTools.readTransitSchedule(transitScheduleFile);
		transitRouteShapeRef.readFile(routeShapeRefFile);
		readShapesFile(shapesFile, gtfsOutputCoordSystem);
	}

	@Override
	public void addShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, RouteShape shape) {
		shapes.put(shape.getId(), shape);
		transitRouteShapeRef.setShapeId(transitLineId, transitRouteId, shape.getId());
	}

	@Override
	public RouteShape getShape(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		Id<RouteShape> shapeId = transitRouteShapeRef.getShapeId(transitLineId, transitRouteId);
		if(shapeId == null) {
			return null;
		}
		return shapes.get(shapeId);
	}

	@Override
	public void readShapesFile(String shapesFilename, String outputCoordinateSystem) {
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem);

		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(shapesFilename));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.SHAPES.columns);
			String[] line = reader.readNext();
			while(line != null) {
				Id<RouteShape> shapeId = Id.create(line[col.get(GtfsDefinitions.SHAPE_ID)], RouteShape.class);
				RouteShape currentShape = shapes.get(shapeId);
				if(currentShape == null) {
					currentShape = new GtfsShape(line[col.get(GtfsDefinitions.SHAPE_ID)]);
					shapes.put(shapeId, currentShape);
				}
				Coord point = new Coord(Double.parseDouble(line[col.get(GtfsDefinitions.SHAPE_PT_LON)]), Double.parseDouble(line[col.get(GtfsDefinitions.SHAPE_PT_LAT)]));
				currentShape.addPoint(ct.transform(point), Integer.parseInt(line[col.get(GtfsDefinitions.SHAPE_PT_SEQUENCE)]));
				line = reader.readNext();
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("File not found!");
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found file!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public TransitRouteShapeReference getTransitRouteShapeReference() {
		return transitRouteShapeRef;
	}

	@Override
	public void setTransitRouteShapeReference(TransitRouteShapeReference ref) {
		this.transitRouteShapeRef = ref;
	}

	@Override
	public void readRouteShapeReferenceFile(String routeShapeRefFile) {
		this.transitRouteShapeRef.readFile(routeShapeRefFile);
	}

	@Override
	public Map<Id<RouteShape>, ? extends RouteShape> getShapes() {
		return this.shapes;
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

}
