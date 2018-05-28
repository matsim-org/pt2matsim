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

package org.matsim.pt2matsim.hafas;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.hafas.lib.*;
import org.matsim.pt2matsim.tools.VehicleTypeDefaults;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts hafas files to a matsim transit schedule.
 *
 * @author polettif
 */
public class HafasConverter {

	protected static Logger log = Logger.getLogger(HafasConverter.class);

	public static void run(String hafasFolder, TransitSchedule schedule, CoordinateTransformation transformation, Vehicles vehicles) throws IOException {
		run(hafasFolder, schedule, transformation, vehicles, -1);
	}

	public static void run(String hafasFolder, TransitSchedule schedule, CoordinateTransformation transformation, Vehicles vehicles, int dayNr) throws IOException {
		if(!hafasFolder.endsWith("/")) hafasFolder += "/";

		log.info("Creating the schedule based on HAFAS...");

		// 1. Read and create stop facilities
		log.info("  Read transit stops...");
		StopReader.run(schedule, transformation, hafasFolder + "BFKOORD_GEO");
		log.info("  Read transit stops... done.");

		// 1.a Read minimal transfer times
		log.info("  Read minimal transfer times...");
		MinimalTransferTimesReader.run(schedule, hafasFolder, "UMSTEIGB","METABHF");
		log.info("  Read minimal transfer times... done.");

		// 2. Read all operators from BETRIEB_DE
		log.info("  Read operators...");
		Map<String, String> operators = OperatorReader.readOperators(hafasFolder + "BETRIEB_DE");
		log.info("  Read operators... done.");

		// 3. Read all ids for work-day-routes from HAFAS-BITFELD
		log.info("  Read bitfeld numbers...");
		Set<Integer> bitfeldNummern;
		if (dayNr < 0) {
			bitfeldNummern = BitfeldAnalyzer.findBitfeldnumbersOfBusiestDay(hafasFolder + "FPLAN", hafasFolder + "BITFELD");
			log.info("      nb of bitfields at busiest day: " + bitfeldNummern.size());
		} else {
			// TODO: check if dayNr is within the timetable period defined in ECKDATEN
			bitfeldNummern = BitfeldAnalyzer.getBitfieldsAtValidDay(dayNr, hafasFolder);
			log.info("      nb of bitfields valid at day " + dayNr + ": " + bitfeldNummern.size());
		}
		log.info("  Read bitfeld numbers... done.");

		// 4. Create all lines from HAFAS-Schedule
		log.info("  Read transit lines...");
		List<FPLANRoute> routes = FPLANReader.parseFPLAN(bitfeldNummern, operators, hafasFolder + "FPLAN");
		log.info("  Read transit lines... done.");

		// TODO another important HAFAS-file is DURCHBI. This feature is not supported by MATSim yet (but in Switzerland, for example, locally very important.

		log.info("  Creating transit routes...");
		createTransitRoutesFromFPLAN(routes, schedule, vehicles);
		log.info("  Creating transit routes... done.");

		// 5. Clean schedule
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		ScheduleCleaner.removeNotUsedMinimalTransferTimes(schedule);
		ScheduleCleaner.combineIdenticalTransitRoutes(schedule);
		ScheduleCleaner.cleanDepartures(schedule);
		ScheduleCleaner.cleanVehicles(schedule, vehicles);

		log.info("Creating the schedule based on HAFAS... done.");
	}

	private static void createTransitRoutesFromFPLAN(List<FPLANRoute> routes, TransitSchedule schedule, Vehicles vehicles) {
		TransitScheduleFactory scheduleFactory = schedule.getFactory();
		VehiclesFactory vehicleFactory = vehicles.getFactory();
		Map<Id<TransitLine>, Integer> routeNrs = new HashMap<>();

		Counter lineCounter = new Counter(" TransitLine # ");

		// set schedule so fplanRoutes have stopfacilities available
		FPLANRoute.setSchedule(schedule);

		for(FPLANRoute fplanRoute : routes) {
			Id<VehicleType> vehicleTypeId = fplanRoute.getVehicleTypeId();

			VehicleTypeDefaults.Type defaultVehicleType = VehicleTypeDefaults.Type.OTHER;

			try {
				defaultVehicleType = VehicleTypeDefaults.Type.valueOf(vehicleTypeId.toString());
			} catch (IllegalArgumentException e) {
				log.warn("Vehicle category '" + vehicleTypeId.toString() + "' is unknown. Falling back to generic OTHER and adding to schedule.");
			}

			// get wheter the route using this vehicle type should be added & set transport mode
			if(defaultVehicleType.addToSchedule) {
				String transportMode = defaultVehicleType.transportMode.name;

				Id<TransitLine> lineId = createLineId(fplanRoute);

				// create or get TransitLine
				TransitLine transitLine;
				if(!schedule.getTransitLines().containsKey(lineId)) {
					transitLine = scheduleFactory.createTransitLine(lineId);
					schedule.addTransitLine(transitLine);
					lineCounter.incCounter();
				} else {
					transitLine = schedule.getTransitLines().get(lineId);
				}

				// create vehicle type if needed
				VehicleType vehicleType = vehicles.getVehicleTypes().get(vehicleTypeId);
				if(vehicleType == null) {
					vehicleType = vehicleFactory.createVehicleType(Id.create(vehicleTypeId.toString(), VehicleType.class));

					// using default values for vehicle type
					vehicleType.setLength(defaultVehicleType.length);
					vehicleType.setWidth(defaultVehicleType.width);
					vehicleType.setAccessTime(defaultVehicleType.accessTime);
					vehicleType.setEgressTime(defaultVehicleType.egressTime);
					vehicleType.setDoorOperationMode(defaultVehicleType.doorOperation);
					vehicleType.setPcuEquivalents(defaultVehicleType.pcuEquivalents);

					VehicleCapacity vehicleCapacity = vehicleFactory.createVehicleCapacity();
					vehicleCapacity.setSeats(defaultVehicleType.capacitySeats);
					vehicleCapacity.setStandingRoom(defaultVehicleType.capacityStanding);
					vehicleType.setCapacity(vehicleCapacity);

					vehicles.addVehicleType(vehicleType);
				}

				// get route id
				int routeNr = MapUtils.getInteger(lineId, routeNrs, 0);
				Id<TransitRoute> routeId = createRouteId(fplanRoute, ++routeNr);
				routeNrs.put(lineId, routeNr);

				// create actual TransitRoute
				TransitRoute transitRoute = scheduleFactory.createTransitRoute(routeId, null, fplanRoute.getTransitRouteStops(), fplanRoute.getMode());
				for(Departure departure : fplanRoute.getDepartures()) {
					transitRoute.addDeparture(departure);
					try {
						vehicles.addVehicle(vehicleFactory.createVehicle(departure.getVehicleId(), vehicleType));
					} catch (Exception e) {
						e.printStackTrace();
						fplanRoute.getDepartures();
					}
				}

				transitRoute.setTransportMode(transportMode);

				transitLine.addRoute(transitRoute);
			}
		}
	}

	private static Id<TransitLine> createLineId(FPLANRoute route) {
		if(route.getOperator().equals("SBB")) {
			long firstStopId;
			long lastStopId;

			// possible S-Bahn number
			String sBahnNr = (route.getVehicleTypeId().toString().equals("S") && route.getRouteDescription() != null) ? route.getRouteDescription() : "";

			try {
				firstStopId = Long.parseLong(route.getFirstStopId());
				lastStopId = Long.parseLong(route.getLastStopId());
			} catch (NumberFormatException e) {
				firstStopId = 0;
				lastStopId = 1;
			}

			if(firstStopId < lastStopId) {
				return Id.create("SBB_" + route.getVehicleTypeId() + sBahnNr + "_" + route.getFirstStopId() + "-" + route.getLastStopId(), TransitLine.class);
			} else {
				return Id.create("SBB_" + route.getVehicleTypeId() + sBahnNr + "_" + route.getLastStopId() + "-" + route.getFirstStopId(), TransitLine.class);
			}
		}
		else if(route.getRouteDescription() == null) {
			return Id.create(route.getOperator(), TransitLine.class);
		} else {
			return Id.create(route.getOperator() + "_line" + route.getRouteDescription(), TransitLine.class);
		}
	}

	private static Id<TransitRoute> createRouteId(FPLANRoute route, int routeNr) {
		return Id.create(route.getFahrtNummer() + "_" + String.format("%03d", routeNr), TransitRoute.class);
	}
}
