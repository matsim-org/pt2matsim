/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.pt2matsim.hafas.lib;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads all stops from HAFAS-BFKOORD_WGS and adds them as TransitStopFacilities
 * to the provided TransitSchedule.
 *
 * @author boescpa
 */
public class StopReader {
	protected static Logger log = LogManager.getLogger(StopReader.class);

	private final CoordinateTransformation transformation;
	private final TransitSchedule schedule;
	private final TransitScheduleFactory scheduleBuilder;
	private final Map<Coord, String> usedCoordinates = new HashMap<>();
	private final String pathToBFKOORD_WGSFile;

	public StopReader(TransitSchedule schedule, CoordinateTransformation transformation, String pathToBFKOORD_WGSFile) {
		this.schedule = schedule;
		this.transformation = transformation;
		this.scheduleBuilder = this.schedule.getFactory();
		this.pathToBFKOORD_WGSFile = pathToBFKOORD_WGSFile;
	}

	public static void run(TransitSchedule schedule, CoordinateTransformation transformation, String pathToBFKOORD_WGSFile) throws IOException {
		new StopReader(schedule, transformation, pathToBFKOORD_WGSFile).createStops();
	}

	private void createStops() throws IOException {
		log.info("  Read transit stops...");
			BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(pathToBFKOORD_WGSFile), "utf-8"));
			String newLine;
			while ((newLine = readsLines.readLine()) != null) {
				if (newLine.startsWith("*")) {
					continue;
				}
				/*
				1−7 INT32 Nummer der Haltestelle
				9−19 FLOAT X-Koordinate
				21−31 FLOAT Y-Koordinate
				33−38 INT16 Z-Koordinate (Tunnel und andere Streckenelemente ohne eigentliche Haltestelle haben keine Z-Koordinate)
				40ff CHAR Kommentarzeichen "%"gefolgt vom Klartext des Haltestellennamens (optional zur besseren Lesbarkeit)
				 */
				Id<TransitStopFacility> stopId = Id.create(newLine.substring(0, 7), TransitStopFacility.class);
				double xCoord = Double.parseDouble(newLine.substring(8, 19));
				double yCoord = Double.parseDouble(newLine.substring(20, 31));
				Coord coord = new Coord(xCoord, yCoord);
				if (this.transformation != null) {
					coord = this.transformation.transform(coord);
				}
				String stopName = newLine.substring(41);
				createStop(stopId, coord, stopName);
			}
			readsLines.close();
		log.info("  Read transit stops... done.");
	}

	private void createStop(Id<TransitStopFacility> stopId, Coord coord, String stopName) {

		//check if coordinates are already used by another facility
		String check = usedCoordinates.put(coord, stopName);
		if(check != null && !check.equals(stopName)) {
			if(check.contains(stopName) || stopName.contains(check)) {
				log.info("Two stop facilities at " + coord + " \"" + check + "\" & \"" + stopName + "\"");
			} else {
				log.warn("Two stop facilities at " + coord + " \"" + check + "\" & \"" + stopName + "\"");
			}
		}

		TransitStopFacility stopFacility = this.scheduleBuilder.createTransitStopFacility(stopId, coord, false);
		stopFacility.setName(stopName);
		this.schedule.addStopFacility(stopFacility);
	}
}
