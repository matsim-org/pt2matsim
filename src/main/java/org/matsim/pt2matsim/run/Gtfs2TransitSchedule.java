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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.time.LocalDate;

import static org.matsim.pt2matsim.gtfs.GtfsConverter.*;

/**
 * Contract class to read GTFS files and convert them to an unmapped MATSim Transit Schedule
 *
 * @author polettif
 */
public final class Gtfs2TransitSchedule {

	protected static Logger log = Logger.getLogger(Gtfs2TransitSchedule.class);

	private Gtfs2TransitSchedule() {
	}

	/**
	 * Reads gtfs files in and converts them to an unmapped
	 * MATSim Transit Schedule (mts). "Unmapped" means stopFacilities are not
	 * referenced to links and transit routes do not have routes (link sequences).
	 * Creates a default vehicles file as well.
	 * <p/>
	 *
	 * @param args	[0] folder where the gtfs files are located (a single zip file is not supported)<br/>
	 * 				[1]	Services from which sample day should be used. One of the following:<br/>
	 *                  <ul>
	 *                  <li>date in the format yyyymmdd</li>
	 *                  <li>dayWithMostTrips (default)</li>
	 *                  <li>dayWithMostServices</li>
	 *                  <li>all</li>
	 *                  </ul>
	 *              [2] the output coordinate system. Use WGS84 for no transformation.<br/>
	 *              [3] output transit schedule file
	 *              [4] output default vehicles file (optional)
	 * Calls {@link #run}.
	 */
	public static void main(final String[] args) {
		if(args.length == 5) {
			run(args[0], args[1], args[2], args[3], args[4]);
		} else if(args.length == 4) {
			run(args[0], args[1], args[2], args[3], null);
		} else {
			throw new IllegalArgumentException("Wrong number of input arguments.");
		}
	}

	/**
	 * Reads gtfs files in and converts them to an unmapped
	 * MATSim Transit Schedule (mts). "Unmapped" means stopFacilities are not
	 * referenced to links and transit routes do not have routes (link sequences).
	 * Creates a default vehicles file as well.
	 * <p/>
	 * @param gtfsFolder          		folder where the gtfs files are located (a single zip file is not supported)
	 * @param sampleDayParam        	Services from which sample day should be used. One of the following:
	 *     				             	<ul>
	 *     				             	<li>date in the format yyyymmdd</li>
	 *     				             	<li>dayWithMostTrips (default)</li>
	 *     				             	<li>dayWithMostServices</li>
	 *     				             	<li>all</li>
	 *     				             	</ul>
	 * @param outputCoordinateSystem 	the output coordinate system. Use WGS84 for no transformation.
	 * @param scheduleFile              output transit schedule file
	 * @param vehicleFile               output default vehicles file (optional)
	 */
	public static void run(String gtfsFolder, String sampleDayParam, String outputCoordinateSystem, String scheduleFile, String vehicleFile) {
		Logger.getLogger(MGC.class).setLevel(Level.ERROR);

		// check sample day parameter
		if(!isValidSampleDayParam(sampleDayParam)) {
			throw new IllegalArgumentException("Sample day parameter not recognized! Allowed: date in format \"yyyymmdd\", " + DAY_WITH_MOST_SERVICES + ", " + DAY_WITH_MOST_TRIPS + ", " + ALL_SERVICE_IDS);
		}
		String param = sampleDayParam == null ? DAY_WITH_MOST_TRIPS : sampleDayParam;

		// load gtfs files
		GtfsFeed gtfsFeed = new GtfsFeedImpl(gtfsFolder);

		// convert to transit schedule
		GtfsConverter converter = new GtfsConverter(gtfsFeed);
		converter.convert(param, outputCoordinateSystem);

		// write Files
		ScheduleTools.writeTransitSchedule(converter.getSchedule(), scheduleFile);
		if(vehicleFile != null) {
			ScheduleTools.writeVehicles(converter.getVehicles(), vehicleFile);
		}
	}

	/**
	 * @return true if <tt>check</tt> is a valid sample day parameter value.
	 */
	private static boolean isValidSampleDayParam(String check) {
		if(check.equals(ALL_SERVICE_IDS) || check.equals(DAY_WITH_MOST_TRIPS) || check.equals(DAY_WITH_MOST_SERVICES)) {
			return true;
		} else {
			try {
				LocalDate.of(Integer.parseInt(check.substring(0, 4)), Integer.parseInt(check.substring(4, 6)), Integer.parseInt(check.substring(6, 8)));
			} catch (NumberFormatException e) {
				return false;
			}
			return true;
		}
	}

}