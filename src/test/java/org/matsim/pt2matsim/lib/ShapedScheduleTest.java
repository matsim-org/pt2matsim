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

import org.junit.Assert;
import org.junit.Test;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * @author polettif
 */
public class ShapedScheduleTest {

	private String input = "test/analysis/";
	private String gtfsFolder = input + "addisoncounty-vt-us-gtfs/";
	private String convertedScheduleFile = input + "shape/schedule_unmapped.xml.gz";
	private String shapeRefFile = input + "shape/shapeRef.csv";


	@Test
	public void convertWriteRead() {
		GtfsFeed gtfsFeed = new GtfsFeedImpl(gtfsFolder);

		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert("all", "EPSG:2032");

		// write
		ShapedTransitSchedule convertedShapedSchedule = gtfsConverter.getShapedTransitSchedule();
		TransitRouteShapeReference convertedShapeRef = convertedShapedSchedule.getTransitRouteShapeReference();
		convertedShapeRef.writeToFile(shapeRefFile);
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), convertedScheduleFile);

		// read
		ShapedTransitSchedule rereadShapedSchedule = new ShapedSchedule(ScheduleTools.readTransitSchedule(convertedScheduleFile));
		TransitRouteShapeReference rereadShapeRef = new TransitRouteShapeReferenceImpl(shapeRefFile);


		for(TransitLine tl : convertedShapedSchedule.getTransitLines().values()) {
			for(TransitRoute tr : tl.getRoutes().values()) {
				Assert.assertEquals(convertedShapedSchedule.getShape(tl.getId(), tr.getId()).getCoords(), (rereadShapedSchedule.getShape(tl.getId(), tr.getId())).getCoords());
				Assert.assertEquals(convertedShapeRef.getShapeId(tl.getId(), tr.getId()), rereadShapeRef.getShapeId(tl.getId(), tr.getId()));
			}
		}
	}

}