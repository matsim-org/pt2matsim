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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.hafas.HafasConverter;
import org.matsim.pt2matsim.hafas.filter.OperationDayFilter;
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
	 *             [4] (optional) chosenDate for which to build schedule, formatted as dd.MM.yyyy<br/>
	 *             [5] (optional) outputNetworkFile<br/>
	 */
	public static void main(String[] args) throws IOException {
		if(args.length == 4) {
			run(args[0], args[1], args[2], args[3], null, null);
		} else if (args.length == 5) {
			if (args[4].endsWith(".xml") || args[4].endsWith(".xml.gz")) {
				run(args[0], args[1], args[2], args[3], null, args[4]);
			} else {
				run(args[0], args[1], args[2], args[3], args[4], null);
			}
		} else if (args.length == 6) {
			run(args[0], args[1], args[2], args[3], args[4], args[5]);
		} else {
			throw new IllegalArgumentException(args.length + " instead of 4, 5 or 6 arguments given");
		}
	}

	/**
	 * Converts all files in <tt>hafasFolder</tt> and writes the output schedule and vehicles to the respective
	 * files. Stop Facility coordinates are transformed from WGS84 to <tt>outputCoordinateSystem</tt>.
	 */
	public static void run(String hafasFolder, String outputCoordinateSystem, String outputScheduleFile, String outputVehicleFile, String chosenDateString, String outputNetworkFile) throws IOException {
		TransitSchedule schedule = ScheduleTools.createSchedule();
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		Network network = outputNetworkFile != null ? NetworkUtils.createNetwork() : null;

		CoordinateTransformation transformation = !outputCoordinateSystem.equals("null") ?
				TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem) : new IdentityTransformation();

		Charset encodingCharset = StandardCharsets.UTF_8;
		OperationDayFilter operationDayFilter = chosenDateString != null
				? new OperationDayFilter(chosenDateString, hafasFolder, encodingCharset)
				: new OperationDayFilter(hafasFolder, encodingCharset);
		HafasConverter.run(hafasFolder, schedule, network, transformation, vehicles, List.of(operationDayFilter), encodingCharset, false, 0.0);

		ScheduleTools.writeTransitSchedule(schedule, outputScheduleFile);
		ScheduleTools.writeVehicles(vehicles, outputVehicleFile);

		if (network != null && outputNetworkFile != null) {
			new NetworkWriter(network).write(outputNetworkFile);
		}
	}
}
