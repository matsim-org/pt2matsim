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
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.tools.GtfsTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.time.LocalDate;
import java.util.*;

/**
 * Converts a GTFS feed to a MATSim transit schedule
 *
 * @author polettif
 */
public class GtfsConverter {

	private final boolean AWAIT_DEPARTURE_TIME_DEFAULT = true;
	private final boolean BLOCKS_DEFAULT = false;

	public static final String ALL_SERVICE_IDS = "all";
	public static final String DAY_WITH_MOST_TRIPS = "dayWithMostTrips";
	public static final String DAY_WITH_MOST_SERVICES = "dayWithMostServices";

	protected static Logger log = Logger.getLogger(GtfsConverter.class);
	private final GtfsFeed feed;
	private final TransitScheduleFactory scheduleFactory = ScheduleTools.createSchedule().getFactory();

	private TransitSchedule transitSchedule;
	private Vehicles vehiclesContainer;

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
		for(Stop stop : this.feed.getStops().values()) {
			TransitStopFacility stopFacility = createStopFacility(stop);
			schedule.addStopFacility(stopFacility);
		}

		// info
		log.info("    Creating TransitLines from routes and TransitRoutes from trips...");
		if(this.feed.usesFrequencies()) {
			log.info("    Using frequencies.txt to generate departures");
		} else {
			log.info("    Using stop_times.txt to generate departures");
		}

		for(Route gtfsRoute : this.feed.getRoutes().values()) {
			// create a MATSim TransitLine for each Route
			TransitLine newTransitLine = createTransitLine(gtfsRoute);
			schedule.addTransitLine(newTransitLine);

			// create TransitRoute for each trip
			for(Trip trip : gtfsRoute.getTrips().values()) {
				// check if the trip actually runs on the extract date
				if(trip.getService().runsOnDate(extractDate)) {
					TransitRoute transitRoute = createTransitRoute(trip, schedule.getFacilities());
					newTransitLine.addRoute(transitRoute);
				}
			}
		}

		// combine TransitRoutes with identical stop/time sequences, add departures
		combineTransitRoutes(schedule);

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

	protected void combineTransitRoutes(TransitSchedule schedule) {
		log.info("Combining TransitRoutes with equal stop sequence and departure offsets...");
		int combined = 0;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			Map<List<String>, List<TransitRoute>> profiles = new HashMap<>();
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				List<String> sequence = new LinkedList<>();
				for(TransitRouteStop routeStop : transitRoute.getStops()) {
					String s = routeStop.getStopFacility().getId().toString() + "-" + (int) routeStop.getDepartureOffset();
					sequence.add(s);
				}
				MapUtils.getList(sequence, profiles).add(transitRoute);
			}

			for(List<TransitRoute> routeList : profiles.values()) {
				if(routeList.size() > 1) {
					TransitRoute finalRoute = routeList.get(0);
					for(int i = 1; i < routeList.size(); i++) {
						TransitRoute routeToRemove = routeList.get(i);
						routeToRemove.getDepartures().values().forEach(finalRoute::addDeparture);
						transitLine.removeRoute(routeToRemove);
						combined++;
					}
				}
			}
		}
		log.info("... Combined " + combined + " transit routes");
	}

	protected TransitRoute createTransitRoute(Trip trip, Map<Id<TransitStopFacility>, TransitStopFacility> stopFacilities) {
		Id<RouteShape> shapeId = trip.getShape() != null ? trip.getShape().getId() : null;

		// Get the stop sequence (with arrivalOffset and departureOffset) of the trip.
		List<TransitRouteStop> transitRouteStops = new ArrayList<>();
		// create transit route stops
		for(StopTime stopTime : trip.getStopTimes()) {
			TransitRouteStop newTransitRouteStop = createTransitRouteStop(stopTime, trip, stopFacilities);
			transitRouteStops.add(newTransitRouteStop);
		}

		// Calculate departures from frequencies (if available)
		TransitRoute transitRoute;
		if(this.feed.usesFrequencies()) {
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

	protected TransitLine createTransitLine(Route gtfsRoute) {
		Id<TransitLine> id = createTransitLineId(gtfsRoute);
		TransitLine line = this.scheduleFactory.createTransitLine(id);
		line.setName(gtfsRoute.getShortName());
		return line;
	}

	protected TransitStopFacility createStopFacility(Stop stop) {
		Id<TransitStopFacility> id = createStopFacilityId(stop);
		TransitStopFacility stopFacility = this.scheduleFactory.createTransitStopFacility(id, stop.getCoord(), BLOCKS_DEFAULT);
		stopFacility.setName(stop.getName());
		stopFacility.setStopPostAreaId(stop.getParentStationId());
		return stopFacility;
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
		ScheduleTools.createVehicles(schedule, vehicles);
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
