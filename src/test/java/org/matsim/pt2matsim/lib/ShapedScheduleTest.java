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

import org.junit.Before;
import org.junit.Test;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * @author polettif
 */
public class ShapedScheduleTest {

	private GtfsConverter gtfsConverter;

	private String input = "test/analysis/";
	String convertedScheduleFile = input + "shape/schedule_unmapped.xml.gz";
	String shapeScheduleFile = input + "shape/schedule_gtfs_shapes.csv";


	@Before
	public void prepare() {
		gtfsConverter = new GtfsConverter(input + "addisoncounty-vt-us-gtfs/", "EPSG:2032");
		gtfsConverter.convert("all");

		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), convertedScheduleFile);
	}

	@Test
	public void checkFiles() {
/*
		ShapedSchedule shapedScheduleConv = gtfsConverter.getShapedSchedule();
		shapedScheduleConv.writeShapeScheduleFile(shapeScheduleFile);

		ShapedSchedule shapedScheduleRead = new ShapedSchedule(ScheduleTools.readTransitSchedule(convertedScheduleFile));
		shapedScheduleRead.readShapeScheduleFile(shapeScheduleFile);

		Assert.equals(shapedScheduleConv.getTransitLines().keySet(), shapedScheduleRead.getTransitLines().keySet());
//		Assert.equals(shapedScheduleConv.getTransitLines().values(), shapedScheduleRead.getTransitLines().values());

		for(TransitLine tl : shapedScheduleConv.getTransitLines().values()) {
			for(TransitRoute tr : tl.getRoutes().values()) {
				Assert.equals(shapedScheduleConv.getShape(tl.getId(), tr.getId()).getPoints(), shapedScheduleRead.getShape(tl.getId(), tr.getId()).getPoints());
				Assert.equals(shapedScheduleConv.getShape(tl.getId(), tr.getId()).getId(), shapedScheduleRead.getShape(tl.getId(), tr.getId()).getId());
				Assert.equals(shapedScheduleConv.getShape(tl.getId(), tr.getId()).getTransitRoutes(), shapedScheduleRead.getShape(tl.getId(), tr.getId()).getTransitRoutes());
			}
		}
		*/
	}
}