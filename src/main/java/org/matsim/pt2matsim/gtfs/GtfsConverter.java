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
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.lib.ShapedSchedule;
import org.matsim.pt2matsim.lib.ShapedTransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Converts a GTFS feed to a MATSim transit schedule
 *
 * @author polettif
 */
public class GtfsConverter {

	protected static Logger log = Logger.getLogger(GtfsConverter.class);

	public static final String ALL_SERVICE_IDS = "all";
	public static final String DAY_WITH_MOST_TRIPS = "dayWithMostTrips";
	public static final String DAY_WITH_MOST_SERVICES = "dayWithMostServices";

	private final GtfsFeed feed;


	private boolean defaultAwaitDepartureTime = true;

	private LocalDate dateUsed = null;

	private ShapedSchedule schedule;
	private Vehicles vhcls;

	/**
	 * The time format used in the output MATSim transit schedule
	 */
	private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");


	public GtfsConverter(GtfsFeed gtfsFeed) {
		this.feed = gtfsFeed;
	}


	public void convert(String serviceIdsParam, String outputCoordinateSystem) {
		convert(serviceIdsParam, TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem), ScheduleTools.createSchedule(), VehicleUtils.createVehiclesContainer());
	}

	public void convert(String serviceIdsParam, String outputCoordinateSystem, TransitSchedule transitSchedule, Vehicles vehicles) {
		convert(serviceIdsParam, TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem), transitSchedule, vehicles);
	}

	public void convert() {
		convert(ALL_SERVICE_IDS, new IdentityTransformation(), ScheduleTools.createSchedule(), VehicleUtils.createVehiclesContainer());
	}

	public TransitSchedule getSchedule() {
		return schedule;
	}

	public Vehicles getVehicles() {
		return vhcls;
	}

	public ShapedTransitSchedule getShapedTransitSchedule() {
		return schedule;
	}



	/**
	 * Converts the loaded gtfs data to a matsim transit schedule
	 * <ol>
	 * <li>generate transitStopFacilities from gtfsStops</li>
	 * <li>Create a transitLine for each GtfsRoute</li>
	 * <li>Generate a transitRoute for each trip</li>
	 * <li>Get the stop sequence of the trip</li>
	 * <li>Calculate departures from stopTimes or frequencies</li>
	 * <li>add transitRoute to the transitLine and thus to the schedule</li>
	 * </ol>
	 */
	public void convert(String serviceIdsParam, CoordinateTransformation transformation, TransitSchedule transitSchedule, Vehicles vehicles) {
		log.info("#####################################");
		log.info("Converting to MATSim transit schedule");

		LocalDate extractDate = getExtractDate(serviceIdsParam);

		/**
		 * The types of dates that will be represented by the new file
		 */
		Set<String> serviceIdsToConvert = getServiceIds(extractDate);

		log.info("    Extracting schedule from date " + extractDate);

		this.schedule = new ShapedSchedule(transitSchedule);
		this.vhcls = vehicles;

		TransitScheduleFactory scheduleFactory = schedule.getFactory();

		int counterLines = 0;
		int counterRoutes = 0;

		/** [1]
		 * generating transitStopFacilities (mts) from gtfsStops and add them to the schedule.
		 * Coordinates are transformed here.
		 */
		for(Map.Entry<String, Stop> stopEntry : feed.getStops().entrySet()) {
			Coord stopFacilityCoord = transformation.transform(stopEntry.getValue().getPoint());
			TransitStopFacility stopFacility = scheduleFactory.createTransitStopFacility(Id.create(stopEntry.getKey(), TransitStopFacility.class), stopFacilityCoord, stopEntry.getValue().isBlocks());
			stopFacility.setName(stopEntry.getValue().getName());
			schedule.addStopFacility(stopFacility);
		}

		if(feed.usesFrequencies()) {
			log.info("    Using frequencies.txt to generate departures");
		} else {
			log.info("    Using stop_times.txt to generate departures");
		}

		DepartureIds departureIds = new DepartureIds();

		for(GtfsRoute gtfsRoute : feed.getRoutes().values()) {
			/** [2]
			 * Create a MTS transitLine for each GtfsRoute
			 */
			TransitLine transitLine = scheduleFactory.createTransitLine(Id.create(gtfsRoute.getShortName() + "_" + gtfsRoute.getRouteId(), TransitLine.class));
			schedule.addTransitLine(transitLine);
			counterLines++;

			/** [3]
			 * loop through each trip for the GTFSroute and generate transitRoute (if the serviceId is correct)
			 */
			for(Trip trip : gtfsRoute.getTrips().values()) {
				boolean isService = false;

				// if trip is part of used serviceId
				for(String serviceId : serviceIdsToConvert) {
					if(trip.getService().equals(feed.getServices().get(serviceId))) {
						isService = true;
					}
				}

				if(isService) {
					/** [4]
					 * Get the stop sequence (with arrivalOffset and departureOffset) of the trip.
					 */
					List<TransitRouteStop> transitRouteStops = new ArrayList<>();
					Date startTime = trip.getStopTimes().get(trip.getStopTimes().firstKey()).getArrivalTime();
					for(StopTime stopTime : trip.getStopTimes().values()) {
						double arrival = Time.UNDEFINED_TIME, departure = Time.UNDEFINED_TIME;

						// add arrival time if current stopTime is not on the first stop of the route
						if(!stopTime.getSequencePosition().equals(trip.getStopTimes().firstKey())) {
							long difference = stopTime.getArrivalTime().getTime() - startTime.getTime();
							try {
								arrival = Time.parseTime(timeFormat.format(new Date(timeFormat.parse("00:00:00").getTime() + difference)));
							} catch (ParseException e) {
								e.printStackTrace();
							}
						}

						// add departure time if current stopTime is not on the last stop of the route
						if(!stopTime.getSequencePosition().equals(trip.getStopTimes().lastKey())) {
							long difference = stopTime.getDepartureTime().getTime() - startTime.getTime();
							try {
								departure = Time.parseTime(timeFormat.format(new Date(timeFormat.parse("00:00:00").getTime() + difference)));
							} catch (ParseException e) {
								e.printStackTrace();
							}
						}
						TransitRouteStop newTRS = scheduleFactory.createTransitRouteStop(schedule.getFacilities().get(Id.create(stopTime.getStopId(), TransitStopFacility.class)), arrival, departure);
						newTRS.setAwaitDepartureTime(defaultAwaitDepartureTime);
						transitRouteStops.add(newTRS);
					}

					/** [5.1]
					 * Calculate departures from frequencies (if available)
					 */
					TransitRoute transitRoute = null;
					if(feed.usesFrequencies()) {
						transitRoute = scheduleFactory.createTransitRoute(Id.create(trip.getId(), TransitRoute.class), null, transitRouteStops, gtfsRoute.getRouteType().name);

						for(Frequency frequency : trip.getFrequencies()) {
							for(Date actualTime = (Date) frequency.getStartTime().clone(); actualTime.before(frequency.getEndTime()); actualTime.setTime(actualTime.getTime() + frequency.getSecondsPerDeparture() * 1000)) {
								transitRoute.addDeparture(scheduleFactory.createDeparture(
										Id.create(departureIds.getNext(transitRoute.getId()), Departure.class),
										Time.parseTime(timeFormat.format(actualTime))));
							}
						}
						transitLine.addRoute(transitRoute);
						counterRoutes++;
					} else {
						/** [5.2]
						 * Calculate departures from stopTimes
						 */

						/* if stop sequence is already used by the same transitLine: just add new departure for the
						 * transitRoute that uses that stop sequence
						 */
						boolean routeExistsInTransitLine = false;
						for(TransitRoute currentTransitRoute : transitLine.getRoutes().values()) {
							if(currentTransitRoute.getStops().equals(transitRouteStops)) {
								currentTransitRoute.addDeparture(scheduleFactory.createDeparture(Id.create(departureIds.getNext(currentTransitRoute.getId()), Departure.class), Time.parseTime(timeFormat.format(startTime))));
								routeExistsInTransitLine = true;
								transitRoute = currentTransitRoute;
								break;
							}
						}

						/* if stop sequence is not used yet, create a new transitRoute (with transitRouteStops)
						 * and add the departure
						 */
						if(!routeExistsInTransitLine) {
							transitRoute = scheduleFactory.createTransitRoute(Id.create(trip.getId(), TransitRoute.class), null, transitRouteStops, gtfsRoute.getRouteType().name);

							transitRoute.addDeparture(scheduleFactory.createDeparture(Id.create(departureIds.getNext(transitRoute.getId()), Departure.class), Time.parseTime(timeFormat.format(startTime))));

							transitLine.addRoute(transitRoute);
							counterRoutes++;
						}
					}

					/* Save transit route (and line) for current shape */
					if(trip.hasShape()) {
						schedule.addShape(transitLine.getId(), transitRoute.getId(), trip.getShape());
					}
				}
			} // foreach trip
		} // foreach route

		/**
		 * Create default vehicles.
		 */
		ScheduleTools.createVehicles(schedule, vhcls);

		log.info("    Created " + counterRoutes + " routes on " + counterLines + " lines.");
		if(dateUsed != null) log.info("    Day " + dateUsed);
		log.info("... GTFS converted to an unmapped MATSIM Transit Schedule");
		log.info("#############################################################");
	}

	/**
	 * @return The date from which services and thus trips should be extracted
	 */
	private LocalDate getExtractDate(String param) {
		switch (param) {
			case ALL_SERVICE_IDS: {
				log.warn("    Using all trips is not recommended");
				log.info("... Using all service IDs");
				return null;
			}

			case DAY_WITH_MOST_SERVICES: {
				log.info("    Using service IDs of the day with the most services (" + DAY_WITH_MOST_SERVICES + ").");
				LocalDate date = null;
				int maxNServiceIds = 0;
				for(Map.Entry<LocalDate, Set<String>> idsOnDayEntry : Service.dateStats.entrySet()) {
					try {
						LocalDate currentDate = idsOnDayEntry.getKey();
						Set<String> currentIds = idsOnDayEntry.getValue();
						if(currentIds.size() > maxNServiceIds) {
							maxNServiceIds = currentIds.size();
							date = currentDate;
						}
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
				return date;
			}

			case DAY_WITH_MOST_TRIPS: {
				log.info("    Using service IDs of the day with the most trips (" + DAY_WITH_MOST_TRIPS + ").");
				LocalDate date = null;
				int maxTrips = 0;
				for(Map.Entry<LocalDate, Set<String>> e : Service.dateStats.entrySet()) {
					int nTrips = 0;
					for(String s : e.getValue()) {
						nTrips += feed.getNTripsPerServiceId().get(s);
					}
					if(nTrips > maxTrips) {
						maxTrips = nTrips;
						date = e.getKey();
					}
				}
				return date;
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

	private Set<String> getServiceIds(LocalDate checkDate) {
		if(checkDate == null) {
			return new HashSet<>(feed.getServices().keySet());
		} else {
			HashSet<String> idsOnCheckDate = new HashSet<>();
			for(Service service : feed.getServices().values()) {
				if(serviceCoversDate(service, checkDate)) {
					idsOnCheckDate.add(service.getId());
				}
			}
			return idsOnCheckDate;
		}
	}

	/**
	 * @return <code>true</code> if the given date is used by the given service.
	 */
	private boolean serviceCoversDate(Service service, LocalDate checkDate) {
		// check if checkDate is an addition
		if(service.getAdditions().contains(checkDate)) {
			return true;
		}
		if(checkDate.isBefore(service.getEndDate()) && checkDate.isAfter(service.getStartDate())) {
			// check if the checkDate is not an exception of the service
			if(service.getExceptions().contains(checkDate)) {
				return false;
			}
			// get weekday (0 = monday)
			int weekday = checkDate.getDayOfWeek().getValue() - 1;
			return service.getDays()[weekday];
		}
		return false;
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
