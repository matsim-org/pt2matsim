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

import com.vividsolutions.jts.util.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;

/**
 * @author polettif
 */
public class ShapeScheduleTest {

	private GtfsConverter gtfsConverter;

	private String input = "test/analysis/";
	String convertedScheduleFile = input + "shape/schedule_unmapped.xml.gz";
	String shapeScheduleFile = input + "shape/schedule_gtfs_shapes.csv";


	@Before
	public void prepare() {
		gtfsConverter = new GtfsConverter(ScheduleTools.createSchedule(), VehicleUtils.createVehiclesContainer(), TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:2032"));
		gtfsConverter.run(input + "addisoncounty-vt-us-gtfs/", "all");

		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), convertedScheduleFile);
	}

	@Test
	public void checkFiles() {
		ShapeSchedule shapeScheduleConv = gtfsConverter.getShapeSchedule();
		shapeScheduleConv.writeShapeScheduleFile(shapeScheduleFile);

		ShapeSchedule shapeScheduleRead = new ShapeSchedule(ScheduleTools.readTransitSchedule(convertedScheduleFile));
		shapeScheduleRead.readShapeScheduleFile(shapeScheduleFile);

		Assert.equals(shapeScheduleConv.getTransitLines().keySet(), shapeScheduleRead.getTransitLines().keySet());
//		Assert.equals(shapeScheduleConv.getTransitLines().values(), shapeScheduleRead.getTransitLines().values());

		for(TransitLine tl : shapeScheduleConv.getTransitLines().values()) {
			for(TransitRoute tr : tl.getRoutes().values()) {
				Assert.equals(shapeScheduleConv.getShape(tl.getId(), tr.getId()).getPoints(), shapeScheduleRead.getShape(tl.getId(), tr.getId()).getPoints());
				Assert.equals(shapeScheduleConv.getShape(tl.getId(), tr.getId()).getId(), shapeScheduleRead.getShape(tl.getId(), tr.getId()).getId());
				Assert.equals(shapeScheduleConv.getShape(tl.getId(), tr.getId()).getTransitRoutes(), shapeScheduleRead.getShape(tl.getId(), tr.getId()).getTransitRoutes());
			}
		}
	}
}