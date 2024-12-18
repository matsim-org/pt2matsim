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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.gtfs.AdditionalTransitLineInfo;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.tools.ScheduleTools;

import com.opencsv.CSVWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Map;

import static org.matsim.pt2matsim.gtfs.GtfsConverter.*;

/**
 * Contract class to read GTFS files and convert them to an unmapped MATSim Transit Schedule
 *
 * @author polettif
 */
public final class Gtfs2TransitSchedule {

	protected static Logger log = LogManager.getLogger(Gtfs2TransitSchedule.class);
	
	private static final String INFO_OUTPUT_OPTION_SCHEDULE = "schedule";

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
	 *              [2] output coordinate system. EPSG:* codes are supported and recommended.
	 *                  Use 'WGS84' for no transformation (though this may lead to errors with PT mapping).<br/>
	 *              [3] output transit schedule file
	 *              [4] (optional) output default vehicles file
	 *              [5] (optional) output for additional line info. One of the following:
	 * 					<ul>
	 *     				<li>empty -> will not be written</li>
	 *     				<li>"schedule" -> will be written as attributable in the schedule</li>
	 *     				<li>output file path (.csv) -> will be written as specified csv file</li>
	 *     				</ul>
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
	 * @param additionalLineInfoFile    output for additional line info (optional). One of the following:
	 * 									<ul>
	 *     				             	<li>empty -> will not be written</li>
	 *     				             	<li>"schedule" -> will be written as attributable in the schedule</li>
	 *     				             	<li>output file path (.csv) -> will be written as specified csv file</li>
	 *     				             	</ul>
	 */
	public static void run(String gtfsFolder, String sampleDayParam, String outputCoordinateSystem, String scheduleFile, String vehicleFile, String additionalLineInfoFile) {
		Configurator.setLevel(LogManager.getLogger(MGC.class).getName(), Level.ERROR);

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

		if (additionalLineInfoFile != null && additionalLineInfoFile.equals(INFO_OUTPUT_OPTION_SCHEDULE)) {
			writeInfoToSchedule(converter.getSchedule(), converter.getAdditionalLineInfo());
		}
		// write Files
		ScheduleTools.writeTransitSchedule(converter.getSchedule(), scheduleFile);
		if(vehicleFile != null) {
			ScheduleTools.writeVehicles(converter.getVehicles(), vehicleFile);
		}
		if (additionalLineInfoFile != null && !additionalLineInfoFile.equals(INFO_OUTPUT_OPTION_SCHEDULE)) {
			writeInfoToFile(additionalLineInfoFile, converter.getAdditionalLineInfo());
		}
	}

	/**
	 * @return true if <tt>check</tt> is a valid sample day parameter value.
	 */
	private static boolean isValidSampleDayParam(String check) {
		if(!check.equals(ALL_SERVICE_IDS) && !check.equals(DAY_WITH_MOST_TRIPS) && !check.equals(DAY_WITH_MOST_SERVICES)) {
			try {
				LocalDate.of(Integer.parseInt(check.substring(0, 4)), Integer.parseInt(check.substring(4, 6)), Integer.parseInt(check.substring(6, 8)));
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return true;
	}
	
	private static void writeInfoToSchedule(TransitSchedule schedule, Map<Id<TransitLine>, AdditionalTransitLineInfo> infos) {
		for (TransitLine line : schedule.getTransitLines().values()) {
			AdditionalTransitLineInfo info = infos.get(line.getId());
			if (info == null) {
				log.warn("Could not find info for transit line " + line.getId().toString());
				return;
			}
			line.getAttributes().putAttribute(AdditionalTransitLineInfo.INFO_COLUMN_LONGNAME, info.getLongName());
			line.getAttributes().putAttribute(AdditionalTransitLineInfo.INFO_COLUMN_TYPE, info.getRouteType().name);
			line.getAttributes().putAttribute(AdditionalTransitLineInfo.INFO_COLUMN_DESCRIPTION, info.getRouteDescription());
			line.getAttributes().putAttribute(AdditionalTransitLineInfo.INFO_COLUMN_AGENCY_ID, info.getAgencyId());
			line.getAttributes().putAttribute(AdditionalTransitLineInfo.INFO_COLUMN_AGENCY_NAME, info.getAgencyName());
			line.getAttributes().putAttribute(AdditionalTransitLineInfo.INFO_COLUMN_AGENCY_URL, info.getAgencyURL());
		}
	}
	
	private static void writeInfoToFile(String filename, Map<Id<TransitLine>, AdditionalTransitLineInfo> infos) {
		try(CSVWriter writer = new CSVWriter(IOUtils.getBufferedWriter(filename))) {
			writer.writeNext(new String[] {
					AdditionalTransitLineInfo.INFO_COLUMN_ID, 
					AdditionalTransitLineInfo.INFO_COLUMN_SHORTNAME,
					AdditionalTransitLineInfo.INFO_COLUMN_LONGNAME,
					AdditionalTransitLineInfo.INFO_COLUMN_TYPE,
					AdditionalTransitLineInfo.INFO_COLUMN_DESCRIPTION,
					AdditionalTransitLineInfo.INFO_COLUMN_AGENCY_ID,
					AdditionalTransitLineInfo.INFO_COLUMN_AGENCY_NAME,
					AdditionalTransitLineInfo.INFO_COLUMN_AGENCY_URL,
					AdditionalTransitLineInfo.INFO_COLUMN_NUM_TRANSIT_ROUTES,
					AdditionalTransitLineInfo.INFO_COLUMN_NUM_TOTAL_DEPARTURES
					});
			for (AdditionalTransitLineInfo info : infos.values()) {
				writer.writeNext(new String[] {
						info.getId(),
						info.getShortName(),
						info.getLongName(),
						info.getRouteType().name,
						info.getRouteDescription(),
						info.getAgencyId(),
						info.getAgencyName(),
						info.getAgencyURL(),
						Integer.toString(info.getNumberOfTransitRoutes()),
						Integer.toString(info.getTotalNumberOfDepartures())
				});
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}