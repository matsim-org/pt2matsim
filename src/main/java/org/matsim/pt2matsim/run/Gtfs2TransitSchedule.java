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
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Contract class to read GTFS files and convert them to an unmapped MATSim Transit Schedule
 *
 * @author polettif
 */
public class Gtfs2TransitSchedule {

	protected static Logger log = Logger.getLogger(Gtfs2TransitSchedule.class);

	/**
	 * Reads gtfs files in and converts them to an unmapped
	 * MATSim Transit Schedule (mts). "Unmapped" means stopFacilities are not
	 * referenced to links and transit routes do not have routes (link sequences).
	 * Creates a default vehicles file as well.
	 * <p/>
	 *
	 * @param args	[0] folder where the gtfs files are located (a single zip file is not supported)<br/>
	 * 				[1]	which service ids should be used. One of the following:<br/>
	 *                  <ul>
	 *                  <li>dayWithMostServices</li>
	 *                  <li>date in the format yyyymmdd</li>
	 *                  <li>dayWithMostTrips</li>
	 *                  <li>all</li>
	 *                  </ul>
	 *              [2] the output coordinate system. Use WGS84 for no transformation.<br/>
	 *              [3] output transit schedule file
	 *              [4] output default vehicles file (optional)
	 *              [5] output shape reference file, CSV file that references transit routes and shapes. Can
	 *              	be used by certain PTMapper implementations and MappingAnalysis (optional)
	 *
	 * Calls {@link #run}.
	 */
	public static void main(final String[] args) {
		if(args.length == 6) {
			run(args[0], args[1], args[2], args[3], args[4], args[5]);
		} else if(args.length == 5) {
			run(args[0], args[1], args[2], args[3], args[4], null);
		} else if(args.length == 4) {
			run(args[0], args[1], args[2], args[3], null, null);
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
	 * @param serviceIdsParam        	which service ids should be used. One of the following:
	 *     				             	<ul>
	 *     				             	<li>dayWithMostServices (default)</li>
	 *     				             	<li>dayWithMostTrips</li>
	 *     				             	<li>date in the format yyyymmdd</li>
	 *     				             	<li>all</li>
	 *     				             	</ul>
	 * @param outputCoordinateSystem 	the output coordinate system. Use WGS84 for no transformation.
	 * @param scheduleFile              output transit schedule file
	 * @param vehicleFile               output default vehicles file (optional)
	 * @param transitRouteShapeRefFile  output route shape reference file. shape files. CSV file
	 *                                  that references transit routes and shapes (optional, output coordinate
	 *                                  system needs to be in EPSG:* format or a name usable by geotools)
	 */
	public static void run(String gtfsFolder, String serviceIdsParam, String outputCoordinateSystem, String scheduleFile, String vehicleFile, String transitRouteShapeRefFile) {
		Logger.getLogger(MGC.class).setLevel(Level.ERROR);

		TransitSchedule schedule = ScheduleTools.createSchedule();
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();

		String param = serviceIdsParam == null ? GtfsFeed.DAY_WITH_MOST_SERVICES : serviceIdsParam;
		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFolder, outputCoordinateSystem);
		gtfsConverter.convert(param, schedule, vehicles);

		boolean authExists = true;
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), scheduleFile);
		if(vehicleFile != null) {
			ScheduleTools.writeVehicles(gtfsConverter.getVehicles(), vehicleFile);
		}
		if(transitRouteShapeRefFile != null) {
			try {
				MGC.getCRS(outputCoordinateSystem);
			} catch (Exception e) {
				authExists = false;
				log.warn("Code " + outputCoordinateSystem + " not recognized by geotools. Shapefile not written.");
			}
			if(authExists)
				gtfsConverter.getShapedSchedule().getTransitRouteShapeReference().writeToFile(transitRouteShapeRefFile);
		}
	}

}