/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.run;

import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.hafas.HafasConverter;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;

/**
 * Run class to convert (Swiss) HAFAS data to
 * an unmapped MATSim Transit Schedule.
 *
 * @author polettif
 */
public final class Hafas2TransitSchedule {

	private Hafas2TransitSchedule() {
	}

	/**
	 * Converts all files in <tt>hafasFolder</tt> and writes the output schedule and vehicles to the respective
	 * files. Stop Facility coordinates are transformed from WGS84 to <tt>outputCoordinateSystem</tt>.
	 *
	 * @param args <br/>
	 *             [0] hafasFolder<br/>
	 *             [1] outputCoordinateSystem. Use <tt>null</tt> if no transformation should be applied.<br/>
	 *             [2] outputScheduleFile<br/>
	 *             [3] outputVehicleFile<br/>
	 */
	public static void main(String[] args) throws IOException {
		if(args.length == 4) {
			run(args[0], args[1], args[2], args[3]);
		} else {
			throw new IllegalArgumentException(args.length + " instead of 4 arguments given");
		}
	}

	/**
	 * Converts all files in <tt>hafasFolder</tt> and writes the output schedule and vehicles to the respective
	 * files. Stop Facility coordinates are transformed from WGS84 to <tt>outputCoordinateSystem</tt>.
	 */
	public static void run(String hafasFolder, String outputCoordinateSystem, String outputScheduleFile, String outputVehicleFile) throws IOException {
		TransitSchedule schedule = ScheduleTools.createSchedule();
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		CoordinateTransformation transformation = !outputCoordinateSystem.equals("null") ?
				TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem) : new IdentityTransformation();

		HafasConverter.run(hafasFolder, schedule, transformation, vehicles);

		ScheduleTools.writeTransitSchedule(schedule, outputScheduleFile);
		ScheduleTools.writeVehicles(vehicles, outputVehicleFile);
	}
}
