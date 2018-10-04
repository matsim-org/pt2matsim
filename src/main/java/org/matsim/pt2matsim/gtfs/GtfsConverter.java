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

package org.matsim.pt2matsim.gtfs;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.tools.GtfsTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.vehicles.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a GTFS feed to a MATSim transit schedule
 *
 * @author polettif
 */
public class GtfsConverter {

	protected final boolean AWAIT_DEPARTURE_TIME_DEFAULT = true;
	protected final boolean BLOCKS_DEFAULT = false;

	public static final String ALL_SERVICE_IDS = "all";
	public static final String DAY_WITH_MOST_TRIPS = "dayWithMostTrips";
	public static final String DAY_WITH_MOST_SERVICES = "dayWithMostServices";

	protected static Logger log = Logger.getLogger(GtfsConverter.class);
	protected final GtfsFeed feed;
	protected final TransitScheduleFactory scheduleFactory = ScheduleTools.createSchedule().getFactory();

	protected TransitSchedule transitSchedule;
	protected Vehicles vehiclesContainer;

	protected int noStopTimeTrips;

	public GtfsConverter(GtfsFeed gtfsFeed) {
		this.feed = gtfsFeed;
	}

	/**
	 * @return the converted schedule (field, see {@link #getSchedule()}}
	 */
	public TransitSchedule convert(String serviceIdsParam, String outputCoordinateSystem) {
		convert(serviceIdsParam, outputCoordinateSystem, ScheduleTools.createSchedule(), VehicleUtils.createVehiclesContainer());
		return getSchedule();
	}


	public TransitSchedule getSchedule() {
		return this.transitSchedule;
	}

	public Vehicles getVehicles() {
		return this.vehiclesContainer;
	}

	/**
	 * Converts the loaded gtfs data to the given matsim transit schedule
	 * <ol>
	 * <li>generate transitStopFacilities from gtfsStops</li>
	 * <li>Create a transitLine for each Route</li>
	 * <li>Generate a transitRoute for each trip</li>
	 * <li>Get the stop sequence of the trip</li>
	 * <li>Calculate departures from stopTimes or frequencies</li>
	 * <li>add transitRoute to the transitLine and thus to the schedule</li>
	 * </ol>
	 */
	public void convert(String serviceIdsParam, String transformation, TransitSchedule schedule, Vehicles vehicles) {
		log.info("#####################################");
		log.info("Converting to MATSim transit schedule");

		// transform feed
		this.feed.transform(transformation);

		// get sample date
		LocalDate extractDate = getExtractDate(serviceIdsParam);
		if(extractDate != null) log.info("     Extracting schedule from date " + extractDate);

		// generate TransitStopFacilities from gtfsStops and add them to the schedule
		createStopFacilities(schedule);

		// create transfers
		createTransfers(schedule);

		// Creating TransitLines from routes and TransitRoutes from trips
		createTransitLines(schedule, extractDate);

		// combine TransitRoutes with identical stop/time sequences, add departures
		combineTransitRoutes(schedule);

		// clean the schedule
		cleanSchedule(schedule);

		// create default vehicles
		createVehicles(schedule, vehicles);

		// statistics
		int counterLines = 0;
		int counterRoutes = 0;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			counterLines++;
			counterRoutes += transitLine.getRoutes().size();
		}
		log.info("    Created " + counterRoutes + " routes on " + counterLines + " lines.");
		if(extractDate != null) log.info("    Day " + extractDate);
		log.info("... GTFS converted to an unmapped MATSIM Transit Schedule");
		log.info("#########################################################");

		this.transitSchedule = schedule;
		this.vehiclesContainer = vehicles;
	}

	protected void createStopFacilities(TransitSchedule schedule) {
		for(Stop stop : this.feed.getStops().values()) {
			TransitStopFacility stopFacility = createStopFacility(stop);
			if(stopFacility != null) {
				schedule.addStopFacility(stopFacility);
			}
		}
	}

	protected void createTransfers(TransitSchedule schedule) {
		MinimalTransferTimes minimalTransferTimes = schedule.getMinimalTransferTimes();

		for(Transfer transfer : feed.getTransfers()) {
			if(!transfer.getTransferType().equals(GtfsDefinitions.TransferType.TRANSFER_NOT_POSSIBLE)) {
				Id<TransitStopFacility> fromStop = Id.create(transfer.getFromStopId(), TransitStopFacility.class);
				Id<TransitStopFacility> toStop = Id.create(transfer.getToStopId(), TransitStopFacility.class);

				// Note: Timed transfer points (type 1) cannot be represented with minimalTransferTimes only
				double minTransferTime = 0;
				if(transfer.getTransferType().equals(GtfsDefinitions.TransferType.REQUIRES_MIN_TRANSFER_TIME)) {
					minTransferTime = transfer.getMinTransferTime();
				}
				minimalTransferTimes.set(fromStop, toStop, minTransferTime);
			}
		}
	}

	/**
	 * @return null if stop should not be converted
	 */
	protected TransitStopFacility createStopFacility(Stop stop) {
		Id<TransitStopFacility> id = createStopFacilityId(stop);
		TransitStopFacility stopFacility = this.scheduleFactory.createTransitStopFacility(id, stop.getCoord(), BLOCKS_DEFAULT);
		stopFacility.setName(stop.getName());
		if(stop.getParentStationId() != null) {
			stopFacility.setStopAreaId(Id.create(stop.getParentStationId(), TransitStopArea.class));
		}
		return stopFacility;
	}

	protected void createTransitLines(TransitSchedule schedule, LocalDate extractDate) {
		// info
		log.info("    Creating TransitLines from routes and TransitRoutes from trips...");

		for(Route gtfsRoute : this.feed.getRoutes().values()) {
			// create a MATSim TransitLine for each Route
			TransitLine newTransitLine = createTransitLine(gtfsRoute);
			if(newTransitLine != null) {
				schedule.addTransitLine(newTransitLine);

				// create TransitRoute for each trip
				for(Trip trip : gtfsRoute.getTrips().values()) {
					// check if the trip actually runs on the extract date
					if(trip.getService().runsOnDate(extractDate)) {
						TransitRoute transitRoute = createTransitRoute(trip, schedule.getFacilities());
						if(transitRoute != null) {
							newTransitLine.addRoute(transitRoute);
						}
					}
				}
			}
		}

		if(noStopTimeTrips > 0) {
			log.warn(noStopTimeTrips + " trips without stop times were not converted");
		}
	}

	/**
	 * @return null if route should not be converted
	 */
	protected TransitLine createTransitLine(Route gtfsRoute) {
		Id<TransitLine> id = createTransitLineId(gtfsRoute);
		TransitLine line = this.scheduleFactory.createTransitLine(id);
		line.setName(gtfsRoute.getShortName());
		return line;
	}

	/**
	 * @return null if route should not be converted
	 */
	protected TransitRoute createTransitRoute(Trip trip, Map<Id<TransitStopFacility>, TransitStopFacility> stopFacilities) {
		Id<RouteShape> shapeId = trip.getShape() != null ? trip.getShape().getId() : null;

		if(trip.getStopTimes().size() == 0) {
			noStopTimeTrips++;
			return null;
		}

		// Get the stop sequence (with arrivalOffset and departureOffset) of the trip.
		List<TransitRouteStop> transitRouteStops = new ArrayList<>();

		// create transit route stops
		for(StopTime stopTime : trip.getStopTimes()) {
			TransitRouteStop newTransitRouteStop = createTransitRouteStop(stopTime, trip, stopFacilities);
			transitRouteStops.add(newTransitRouteStop);
		}

		// Calculate departures from frequencies (if available)
		TransitRoute transitRoute;
		if(trip.getFrequencies().size() > 0) {
			transitRoute = this.scheduleFactory.createTransitRoute(createTransitRouteId(trip), null, transitRouteStops, trip.getRoute().getRouteType().name);

			for(Frequency frequency : trip.getFrequencies()) {
				for(int t = frequency.getStartTime(); t < frequency.getEndTime(); t += frequency.getHeadWaySecs()) {
					Departure newDeparture = this.scheduleFactory.createDeparture(createDepartureId(transitRoute, t), t);
					transitRoute.addDeparture(newDeparture);
				}
			}
			return transitRoute;
		} else {
			// Calculate departures from stopTimes
			int routeStartTime = trip.getStopTimes().first().getDepartureTime();

			transitRoute = this.scheduleFactory.createTransitRoute(createTransitRouteId(trip), null, transitRouteStops, trip.getRoute().getRouteType().name);
			Departure newDeparture = this.scheduleFactory.createDeparture(createDepartureId(transitRoute, routeStartTime), routeStartTime);
			transitRoute.addDeparture(newDeparture);

			if(shapeId != null) ScheduleTools.setShapeId(transitRoute, trip.getShape().getId());

			return transitRoute;
		}
	}

	protected TransitRouteStop createTransitRouteStop(StopTime stopTime, Trip trip, Map<Id<TransitStopFacility>, TransitStopFacility> stopFacilities) {
		double arrivalOffset = Time.UNDEFINED_TIME, departureOffset = Time.UNDEFINED_TIME;

		int routeStartTime = trip.getStopTimes().first().getArrivalTime();
		int firstSequencePos = trip.getStopTimes().first().getSequencePosition();
		int lastSequencePos = trip.getStopTimes().last().getSequencePosition();


		// add arrivalOffset time if current stopTime is not on the first stop of the route
		if(!stopTime.getSequencePosition().equals(firstSequencePos)) {
			arrivalOffset = stopTime.getArrivalTime() - routeStartTime;
		}

		// add departure time if current stopTime is not on the last stop of the route
		if(!stopTime.getSequencePosition().equals(lastSequencePos)) {
			departureOffset = stopTime.getArrivalTime() - routeStartTime;
		}

		TransitStopFacility stopFacility = stopFacilities.get(createStopFacilityId(stopTime.getStop()));

		TransitRouteStop newTransitRouteStop = this.scheduleFactory.createTransitRouteStop(stopFacility, arrivalOffset, departureOffset);
		newTransitRouteStop.setAwaitDepartureTime(AWAIT_DEPARTURE_TIME_DEFAULT);
		return newTransitRouteStop;
	}

	protected void combineTransitRoutes(TransitSchedule schedule) {
		ScheduleCleaner.combineIdenticalTransitRoutes(schedule);
	}

	protected void cleanSchedule(TransitSchedule schedule) {
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		ScheduleCleaner.removeNotUsedMinimalTransferTimes(schedule);
	}

	protected Id<TransitLine> createTransitLineId(Route gtfsRoute) {
		String id = gtfsRoute.getId();
		return Id.create(id, TransitLine.class);
	}

	protected Id<TransitRoute> createTransitRouteId(Trip trip) {
		return Id.create(trip.getId(), TransitRoute.class);
	}

	protected Id<TransitStopFacility> createStopFacilityId(Stop stop) {
		return Id.create(stop.getId(), TransitStopFacility.class);
	}

	protected Id<Departure> createDepartureId(TransitRoute route, int time) {
		String str = route.getId().toString() + "_" + Time.writeTime(time, "HH:mm:ss");
		return Id.create(str, Departure.class);
	}

	protected void createVehicles(TransitSchedule schedule, Vehicles vehicles) {
		VehiclesFactory vf = vehicles.getFactory();
		Map<GtfsDefinitions.ExtendedRouteType, VehicleType> vehicleTypes = new HashMap<>();

		long vehId = 0;
		for(TransitLine line : schedule.getTransitLines().values()) {
			// get extended route type
			Route gtfsRoute = feed.getRoutes().get(line.getId().toString());
			GtfsDefinitions.ExtendedRouteType extType = gtfsRoute.getExtendedRouteType();

			// create vehicle type for each extended route type
			if(!vehicleTypes.containsKey(extType)) {
				VehicleType defaultVehicleType = ScheduleTools.createDefaultVehicleType(extType.name, extType.routeType.name);
				vehicles.addVehicleType(defaultVehicleType);
				vehicleTypes.put(extType, defaultVehicleType);
			}

			VehicleType vehicleType = vehicleTypes.get(extType);
			for(TransitRoute route : line.getRoutes().values()) {
				// create a vehicle for each departure
				for(Departure departure : route.getDepartures().values()) {
					String vehicleId = "veh_" + Long.toString(vehId++) + "_" + route.getTransportMode().replace(" ", "_");
					Vehicle veh = vf.createVehicle(Id.create(vehicleId, Vehicle.class), vehicleType);
					vehicles.addVehicle(veh);
					departure.setVehicleId(veh.getId());
				}
			}
		}
	}

	/**
	 * @return The date from which services and thus trips should be extracted
	 */
	protected LocalDate getExtractDate(String param) {
		switch(param) {
			case ALL_SERVICE_IDS: {
				log.warn("    Using all trips is not recommended");
				log.info("... Using all service IDs");
				return null;
			}

			case DAY_WITH_MOST_SERVICES: {
				log.info("    Using service IDs of the day with the most services (" + DAY_WITH_MOST_SERVICES + ").");
				return GtfsTools.getDayWithMostServices(feed);
			}

			case DAY_WITH_MOST_TRIPS: {
				log.info("    Using service IDs of the day with the most trips (" + DAY_WITH_MOST_TRIPS + ").");
				return GtfsTools.getDayWithMostTrips(feed);
			}

			default: {
				LocalDate date;
				try {
					date = LocalDate.of(Integer.parseInt(param.substring(0, 4)), Integer.parseInt(param.substring(4, 6)), Integer.parseInt(param.substring(6, 8)));
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Extract param not recognized");
				}
				return date;
			}
		}
	}
}
