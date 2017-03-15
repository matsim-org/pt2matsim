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

package org.matsim.pt2matsim.run;

import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.osm.OsmTransitScheduleConverter;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * @author polettif
 */
public final class Osm2TransitSchedule {

	private Osm2TransitSchedule() {
	}

	/**
	 * Converts the available public transit data of an osm file to a MATSim transit schedule
	 * @param args [0] osm file
	 *             [1] output schedule file
	 *             [2] output coordinate system (optional)
	 */
	public static void main(final String[] args) {
		if(args.length == 2) {
			run(args[0], args[1], null);
		} else if(args.length == 3) {
			run(args[0], args[1], args[2]);
		} else {
			throw new IllegalArgumentException("Wrong number of arguments");
		}
	}

	public static void run(String osmFile, String outputScheduleFile, String outputCoordinateSystem) {
		TransitSchedule schedule = ScheduleTools.createSchedule();
		CoordinateTransformation ct = outputCoordinateSystem != null ? TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem) : null;

		// load osm file
		OsmData osmData = new OsmDataImpl();
		new OsmFileReader(osmData).readFile(osmFile);

		// convert osm data
		new OsmTransitScheduleConverter(osmData).convert(schedule, ct);

		// write file
		ScheduleTools.writeTransitSchedule(schedule, outputScheduleFile);
	}
}
