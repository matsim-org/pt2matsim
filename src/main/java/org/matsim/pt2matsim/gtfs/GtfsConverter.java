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
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.tools.GtfsTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

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

	public static final String ALL_SERVICE_IDS = "all";
	public static final String DAY_WITH_MOST_TRIPS = "dayWithMostTrips";
	public static final String DAY_WITH_MOST_SERVICES = "dayWithMostServices";
	protected static Logger log = Logger.getLogger(GtfsConverter.class);
	private final boolean AWAIT_DEPARTURE_TIME_DEFAULT = true;
	private final boolean BLOCKS_DEFAULT = false;
	private final GtfsFeed feed;

	private LocalDate dateUsed = null;

	private TransitSchedule schedule;
	private Vehicles vhcls;

	public GtfsConverter(GtfsFeed gtfsFeed) {
		this.feed = gtfsFeed;
	}

	public TransitSchedule convert(String serviceIdsParam, String outputCoordinateSystem) {
		TransitSchedule schedule = ScheduleTools.createSchedule();
		convert(serviceIdsParam, outputCoordinateSystem, schedule, VehicleUtils.createVehiclesContainer());
		return schedule;
	}


	public TransitSchedule getSchedule() {
		return schedule;
	}

	public Vehicles getVehicles() {
		return vhcls;
	}

	/**
	 * Converts the loaded gtfs data to a matsim transit schedule
	 * <ol>
	 * <li>generate transitStopFacilities from gtfsStops</li>
	 * <li>Create a transitLine for each Route</li>
	 * <li>Generate a transitRoute for each trip</li>
	 * <li>Get the stop sequence of the trip</li>
	 * <li>Calculate departures from stopTimes or frequencies</li>
	 * <li>add transitRoute to the transitLine and thus to the schedule</li>
	 * </ol>
	 */
	public void convert(String serviceIdsParam, String transformation, TransitSchedule transitSchedule, Vehicles vehicles) {
		log.info("#####################################");
		log.info("Converting to MATSim transit schedule");

		LocalDate extractDate = getExtractDate(serviceIdsParam);

		if(extractDate != null) log.info("    Extracting schedule from date " + extractDate);

		this.schedule = transitSchedule;
		this.vhcls = vehicles;

		TransitScheduleFactory scheduleFactory = schedule.getFactory();

		int counterLines = 0;
		int counterRoutes = 0;

		feed.transform(transformation);

		/* [1]
		  generating transitStopFacilities (mts) from gtfsStops and add them to the schedule.
		  Coordinates are transformed here.
		 */
		for(Map.Entry<String, Stop> stopEntry : feed.getStops().entrySet()) {
			TransitStopFacility stopFacility = scheduleFactory.createTransitStopFacility(Id.create(stopEntry.getKey(), TransitStopFacility.class), stopEntry.getValue().getCoord(), BLOCKS_DEFAULT);
			stopFacility.setName(stopEntry.getValue().getName());
			schedule.addStopFacility(stopFacility);
		}

		if(feed.usesFrequencies()) {
			log.info("    Using frequencies.txt to generate departures");
		} else {
			log.info("    Using stop_times.txt to generate departures");
		}

		DepartureIds departureIds = new DepartureIds();

		for(Route gtfsRoute : feed.getRoutes().values()) {
			/* [2]
			  Create a MTS transitLine for each Route
			 */
			TransitLine transitLine = scheduleFactory.createTransitLine(Id.create(gtfsRoute.getShortName() + "_" + gtfsRoute.getId(), TransitLine.class));
			schedule.addTransitLine(transitLine);
			counterLines++;

			Map<Id<TransitRoute>, Id<RouteShape>> routeShapeAssignment = new HashMap<>();

			/* [3]
			  loop through each trip for the gtfsRoute and generate transitRoute (if the serviceId is correct)
			 */
			for(Trip trip : gtfsRoute.getTrips().values()) {
				Id<RouteShape> shapeId = trip.hasShape() ? trip.getShape().getId() : null;

				if(trip.getService().runsOnDate(extractDate)) {
					/* [4]
					  Get the stop sequence (with arrivalOffset and departureOffset) of the trip.
					 */
					List<TransitRouteStop> transitRouteStops = new ArrayList<>();
					int startTime = trip.getStopTimes().first().getArrivalTime();
					int firstSequencePos = trip.getStopTimes().first().getSequencePosition();
					int lastSequencePos = trip.getStopTimes().last().getSequencePosition();

					for(StopTime stopTime : trip.getStopTimes()) {
						double arrivalOffset = Time.UNDEFINED_TIME, departureOffset = Time.UNDEFINED_TIME;

						// add arrivalOffset time if current stopTime is not on the first stop of the route
						if(!stopTime.getSequencePosition().equals(firstSequencePos)) {
							arrivalOffset = stopTime.getArrivalTime() - startTime;
						}

						// add departure time if current stopTime is not on the last stop of the route
						if(!stopTime.getSequencePosition().equals(lastSequencePos)) {
							departureOffset = stopTime.getArrivalTime() - startTime;
						}
						TransitRouteStop newTRS = scheduleFactory.createTransitRouteStop(schedule.getFacilities().get(Id.create(stopTime.getStop().getId(), TransitStopFacility.class)), arrivalOffset, departureOffset);
						newTRS.setAwaitDepartureTime(AWAIT_DEPARTURE_TIME_DEFAULT);
						transitRouteStops.add(newTRS);
					}

					/* [5.1]
					  Calculate departures from frequencies (if available)
					 */
					TransitRoute transitRoute;
					if(feed.usesFrequencies()) {
						transitRoute = scheduleFactory.createTransitRoute(Id.create(trip.getId(), TransitRoute.class), null, transitRouteStops, gtfsRoute.getRouteType().name);

						for(Frequency frequency : trip.getFrequencies()) {
							for(int t = frequency.getStartTime(); t < frequency.getEndTime(); t += frequency.getHeadWaySecs()) {
								transitRoute.addDeparture(scheduleFactory.createDeparture(
										Id.create(departureIds.getNext(transitRoute.getId()), Departure.class),
										t));
							}
						}
						transitLine.addRoute(transitRoute);
						counterRoutes++;
					} else {

						/* [5.2]
						  Calculate departures from stopTimes
						 */

						/* if stop sequence is already used in the same transitLine: just add new departure for the
						 * transitRoute that uses that stop sequence
						 */
						boolean createNewTransitRoute = true;

						for(TransitRoute currentTransitRoute : transitLine.getRoutes().values()) {
							if(currentTransitRoute.getStops().equals(transitRouteStops)) {
								if(routeShapeAssignment.get(currentTransitRoute.getId()) == shapeId) {
									currentTransitRoute.addDeparture(scheduleFactory.createDeparture(Id.create(departureIds.getNext(currentTransitRoute.getId()), Departure.class), startTime));
									createNewTransitRoute = false;
									break;
								}
							}
						}

						/* if stop sequence is not used yet, create a new transitRoute (with transitRouteStops)
						 * and add the departure
						 */
						if(createNewTransitRoute) {
							transitRoute = scheduleFactory.createTransitRoute(Id.create(trip.getId(), TransitRoute.class), null, transitRouteStops, gtfsRoute.getRouteType().name);
							transitRoute.addDeparture(scheduleFactory.createDeparture(Id.create(departureIds.getNext(transitRoute.getId()), Departure.class), startTime));

							if(shapeId != null) ScheduleTools.setShapeId(transitRoute, trip.getShape().getId());
							routeShapeAssignment.put(transitRoute.getId(), shapeId);

							transitLine.addRoute(transitRoute);
							counterRoutes++;
						}
					}
				}
			} // foreach trip
		} // foreach route

		/*
		  Create default vehicles.
		 */
		ScheduleTools.createVehicles(schedule, vhcls);

		log.info("    Created " + counterRoutes + " routes on " + counterLines + " lines.");
		if(dateUsed != null) log.info("    Day " + dateUsed);
		log.info("... GTFS converted to an unmapped MATSIM Transit Schedule");
		log.info("#########################################################");
	}


	/**
	 * @return The date from which services and thus trips should be extracted
	 */
	private LocalDate getExtractDate(String param) {
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

	/**
	 * helper class for meaningful departureIds
	 */
	private class DepartureIds {

		private Map<Id<TransitRoute>, Integer> ids = new HashMap<>();

		String getNext(Id<TransitRoute> transitRouteId) {
			if(!ids.containsKey(transitRouteId)) {
				ids.put(transitRouteId, 1);
				return transitRouteId + "_01";
			} else {
				int i = ids.put(transitRouteId, ids.get(transitRouteId) + 1) + 1;
				return transitRouteId + "_" + String.format("%03d", i);
			}

		}
	}
}
