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

import com.opencsv.CSVReader;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.lib.RouteShape;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;


/**
 * Reads GTFS files and stores data
 * <p/>
 * Based on GTFS2MATSimTransitSchedule by Sergio Ordonez
 *
 * @author polettif
 */
public class GtfsFeedImpl implements GtfsFeed {

	private static final Logger log = Logger.getLogger(GtfsFeedImpl.class);

	/**
	 * Path to the folder where the gtfs files are located
	 */
	private String root;

	/**
	 * whether the gtfs feed uses frequencies.txt or not
	 */
	private boolean usesFrequencies = false;

	/**
	 * whether the gtfs feed uses shapes or not
	 */
	private boolean usesShapes = false;

	/**
	 * Set of service ids not defined in calendar.txt (only in calendar_dates.txt)
	 */
	private Set<String> serviceIdsNotInCalendarTxt = new HashSet<>();

	private DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

	// containers for storing gtfs data
	private Map<String, Stop> gtfsStops = new HashMap<>();
	private Map<String, Route> routes = new TreeMap<>();
	private Map<String, Service> services = new HashMap<>();
	private Map<LocalDate, Set<Service>> serviceDateStat = new HashMap<>();
	private Map<LocalDate, Set<Trip>> tripDateStat = new HashMap<>();
	private Map<String, Trip> trips = new HashMap<>();
	private Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();
	private boolean warnStopTimes = true;

	public GtfsFeedImpl(String gtfsFolder) {
		loadFiles(gtfsFolder);
	}

	/**
	 * In case optional columns in a csv file are missing or are out of order, addressing array
	 * values directly via integer (i.e. where the column should be) does not work.
	 *
	 * @param header          the header (first line) of the csv file
	 * @param requiredColumns array of attributes you need the indices of
	 * @return the index for each attribute given in columnNames
	 */
	private static Map<String, Integer> getIndices(String[] header, String[] requiredColumns, String[] optionalColumns) {
		Map<String, Integer> indices = new HashMap<>();
		Set<String> requiredNotFound = new HashSet<>();

		for(String columnName : requiredColumns) {
			boolean found = false;
			for(int i = 0; i < header.length; i++) {
				if(header[i].equals(columnName)) {
					indices.put(columnName, i);
					found = true;
					break;
				}
			}
			if(!found) {
				requiredNotFound.add(columnName);
			}
		}

		if(requiredNotFound.size() > 0) {
			throw new IllegalArgumentException("Required column(s) " + requiredNotFound + " not found in csv. Might be some additional characters in the header or the encoding not being UTF-8.");
		}

		for(String columnName : optionalColumns) {
			for(int i = 0; i < header.length; i++) {
				if(header[i].equals(columnName)) {
					indices.put(columnName, i);
					break;
				}
			}
		}
		return indices;
	}

	/**
	 * Calls all methods to load the gtfs files. Order is critical
	 */
	private void loadFiles(String inputPath) {
		if(!inputPath.endsWith("/")) inputPath += "/";
		this.root = inputPath;

		log.info("Loading GTFS files from " + root);
		try { loadStops(); } catch (IOException e) {
			throw new RuntimeException("File stops.txt not found!");
		}
		try { loadCalendar(); } catch (IOException e) {
			throw new RuntimeException("File calendar.txt not found! ");
		}
		loadCalendarDates();
		loadShapes();
		try { loadRoutes(); } catch (IOException e) {
			throw new RuntimeException("File routes.txt not found!");
		}
		try { loadTrips(); } catch (IOException e) {
			throw new RuntimeException("File trips.txt not found!");
		}
		try { loadStopTimes(); } catch (IOException e) {
			throw new RuntimeException("File stop_times.txt not found!");
		}
		loadFrequencies();
		calcDateStats();
		log.info("All files loaded");
	}

	/**
	 * Reads all stops and puts them in {@link #gtfsStops}
	 * <p/>
	 * <br/><br/>
	 * stops.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Individual locations where vehicles pick up or drop off passengers.
	 *
	 * @throws IOException
	 */
	private void loadStops() throws IOException {
		log.info("Loading stops.txt");
		CSVReader reader;

		try {
			reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.STOPS.fileName));
			String[] header = reader.readNext(); // read header
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.STOPS.columns, GtfsDefinitions.Files.STOPS.optionalColumns); // get column numbers for required fields

			String[] line = reader.readNext();
			while(line != null) {
				String stopId = line[col.get(GtfsDefinitions.STOP_ID)];
				Coord coord = new Coord(Double.parseDouble(line[col.get(GtfsDefinitions.STOP_LON)]), Double.parseDouble(line[col.get(GtfsDefinitions.STOP_LAT)]));
				Stop GtfsStop = new StopImpl(stopId, line[col.get(GtfsDefinitions.STOP_NAME)], coord);
				gtfsStops.put(stopId, GtfsStop);

				line = reader.readNext();
			}

			reader.close();
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in stops.txt");
		}
		log.info("...     stops.txt loaded");
	}

	/**
	 * Reads all services and puts them in {@link #services}
	 * <p/>
	 * <br/><br/>
	 * calendar.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Dates for service IDs using a weekly schedule. Specify when service starts and ends,
	 * as well as days of the week where service is available.
	 *
	 * @throws IOException
	 */
	private void loadCalendar() throws IOException {
		log.info("Loading calendar.txt");
		try {
			CSVReader reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.CALENDAR.fileName));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.CALENDAR.columns, GtfsDefinitions.Files.CALENDAR.optionalColumns);

			// assuming all days really do follow monday in the file
			int indexMonday = col.get("monday");

			String[] line = reader.readNext();
			int i = 1, c = 1;
			while(line != null) {
				if(i == Math.pow(2, c)) {
					log.info("        # " + i);
					c++;
				}
				i++;

				boolean[] days = new boolean[7];
				for(int d = 0; d < 7; d++) {
					days[d] = line[indexMonday + d].equals("1");
				}
				services.put(line[col.get(GtfsDefinitions.SERVICE_ID)], new ServiceImpl(line[col.get(GtfsDefinitions.SERVICE_ID)], days, line[col.get(GtfsDefinitions.START_DATE)], line[col.get(GtfsDefinitions.END_DATE)]));

				line = reader.readNext();
			}

			reader.close();
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in calendar.txt");
		}
		log.info("...     calendar.txt loaded");
	}

	/**
	 * Adds service exceptions to {@link #services} (if available)
	 * <p/>
	 * <br/><br/>
	 * calendar_dates.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Exceptions for the service IDs defined in the calendar.txt file. If calendar_dates.txt includes ALL
	 * dates of service, this file may be specified instead of calendar.txt.
	 */
	private void loadCalendarDates() {
		// calendar dates are optional
		log.info("Looking for calendar_dates.txt");
		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.CALENDAR_DATES.fileName));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.CALENDAR_DATES.columns, GtfsDefinitions.Files.CALENDAR_DATES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				Service currentService = services.get(line[col.get(GtfsDefinitions.SERVICE_ID)]);

				if(currentService == null) {
					currentService = new ServiceImpl(line[col.get(GtfsDefinitions.SERVICE_ID)]);

					services.put(currentService.getId(), currentService);

					if(serviceIdsNotInCalendarTxt.add(currentService.getId())) {
						log.warn("Service id \"" + currentService.getId() + "\" not defined in calendar.txt, only in calendar_dates.txt. Service id will still be used.");
					}
				}

				if(line[col.get(GtfsDefinitions.EXCEPTION_TYPE)].equals("2")) {
					currentService.addException(line[col.get(GtfsDefinitions.DATE)]);
				} else {
					currentService.addAddition(line[col.get(GtfsDefinitions.DATE)]);
				}

				line = reader.readNext();
			}
			reader.close();
			log.info("...     calendar_dates.txt loaded");
		} catch (IOException e) {
			log.info("...     no calendar dates file found.");
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in calendar_dates.txt");
		}
	}

	/**
	 * Loads shapes (if available) and puts them in {@link #shapes}. A shape is a sequence of points, i.e. a line.
	 * <p/>
	 * <br/><br/>
	 * shapes.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Rules for drawing lines on a map to represent a transit organization's routes.
	 */
	private void loadShapes() {
		// shapes are optional
		log.info("Looking for shapes.txt");
		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.SHAPES.fileName));

			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.SHAPES.columns, GtfsDefinitions.Files.SHAPES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				usesShapes = true; // shape file might exists but could be empty

				Id<RouteShape> shapeId = Id.create(line[col.get(GtfsDefinitions.SHAPE_ID)], RouteShape.class);

				RouteShape currentShape = shapes.get(shapeId);
				if(currentShape == null) {
					currentShape = new GtfsShape(line[col.get(GtfsDefinitions.SHAPE_ID)]);
					shapes.put(shapeId, currentShape);
				}
				Coord point = new Coord(Double.parseDouble(line[col.get(GtfsDefinitions.SHAPE_PT_LON)]), Double.parseDouble(line[col.get(GtfsDefinitions.SHAPE_PT_LAT)]));
				currentShape.addPoint(point, Integer.parseInt(line[col.get(GtfsDefinitions.SHAPE_PT_SEQUENCE)]));
				line = reader.readNext();
			}
			reader.close();
			log.info("...     shapes.txt loaded");
		} catch (IOException e) {
			log.info("...     no shapes file found.");
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in shapes.txt");
		}
	}

	/**
	 * Basically just reads all routeIds and their corresponding names and types and puts them in {@link #routes}.
	 * <p/>
	 * <br/><br/>
	 * routes.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Transit routes. A route is a group of trips that are displayed to riders as a single service.
	 *
	 * @throws IOException
	 */
	private void loadRoutes() throws IOException {
		log.info("Loading routes.txt");
		try {
			CSVReader reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.ROUTES.fileName));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.ROUTES.columns, GtfsDefinitions.Files.ROUTES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				int routeTypeNr = Integer.parseInt(line[col.get(GtfsDefinitions.ROUTE_TYPE)]);
				if(routeTypeNr < 0 || routeTypeNr > 7) {
					throw new RuntimeException("Invalid value for route type: " + routeTypeNr + " [https://developers.google.com/transit/gtfs/reference/routes-file]");
				}

				Route newGtfsRoute = new RouteImpl(line[col.get(GtfsDefinitions.ROUTE_ID)], line[col.get(GtfsDefinitions.ROUTE_SHORT_NAME)], GtfsDefinitions.RouteTypes.values()[routeTypeNr]);
				routes.put(line[col.get(GtfsDefinitions.ROUTE_ID)], newGtfsRoute);

				line = reader.readNext();
			}
			reader.close();
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in routes.txt");
		}
		log.info("...     routes.txt loaded");
	}

	/**
	 * Generates a trip with trip_id and adds it to the corresponding route (referenced by route_id) in {@link #routes}.
	 * Adds the shape_id as well (if shapes are used). Each trip uses one service_id, the serviceIds statistics are increased accordingly
	 * <p/>
	 * <br/><br/>
	 * trips.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Trips for each route. A trip is a sequence of two or more gtfsStops that occurs at specific time.
	 *
	 * @throws IOException
	 */
	private void loadTrips() throws IOException {
		log.info("Loading trips.txt");
		try {
			CSVReader reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.TRIPS.fileName));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.TRIPS.columns, GtfsDefinitions.Files.TRIPS.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				Trip newTrip;
				Route gtfsRoute = routes.get(line[col.get("route_id")]);
				Service service = services.get(line[col.get(GtfsDefinitions.SERVICE_ID)]);

				if(usesShapes) {
					Id<RouteShape> shapeId = Id.create(line[col.get(GtfsDefinitions.SHAPE_ID)], RouteShape.class); // column might not be available
					newTrip = new TripImpl(line[col.get(GtfsDefinitions.TRIP_ID)], service, shapes.get(shapeId), line[col.get(GtfsDefinitions.TRIP_ID)]);
				} else {
					newTrip = new TripImpl(line[col.get(GtfsDefinitions.TRIP_ID)], service, null, line[col.get(GtfsDefinitions.TRIP_ID)]);
				}

				// store Trip
				gtfsRoute.addTrip(newTrip);
				service.addTrip(newTrip);
				trips.put(newTrip.getId(), newTrip);

				line = reader.readNext();
			}

			reader.close();
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in trips.txt");
		}
		log.info("...     trips.txt loaded");
	}

	/**
	 * Stop times are added to their respective trip (which are stored in {@link #routes}).
	 * <p/>
	 * <br/><br/>
	 * stop_times.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Times that a vehicle arrives at and departs from individual gtfsStops for each trip.
	 *
	 * @throws IOException
	 */
	private void loadStopTimes() throws IOException {
		log.info("Loading stop_times.txt");
		try {
			CSVReader reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.STOP_TIMES.fileName));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.STOP_TIMES.columns, GtfsDefinitions.Files.STOP_TIMES.optionalColumns);

			String[] line = reader.readNext();
			int i = 1, c = 1;
			while(line != null) {
				if(i == Math.pow(2, c)) { log.info("        # " + i); c++; } i++; // just for logging so something happens in the console

				for(Route currentGtfsRoute : routes.values()) {
					Trip trip = currentGtfsRoute.getTrips().get(line[col.get(GtfsDefinitions.TRIP_ID)]);
					if(trip != null) {
						try {
							if(!line[col.get(GtfsDefinitions.ARRIVAL_TIME)].equals("")) {
								StopTime newStopTime = new StopTimeImpl(Integer.parseInt(line[col.get(GtfsDefinitions.STOP_SEQUENCE)]),
										timeFormat.parse(line[col.get(GtfsDefinitions.ARRIVAL_TIME)]),
										timeFormat.parse(line[col.get(GtfsDefinitions.DEPARTURE_TIME)]),
										line[col.get(GtfsDefinitions.STOP_ID)],
										line[col.get(GtfsDefinitions.TRIP_ID)]);
								trip.addStopTime(newStopTime);
							}
							/** GTFS Reference: If this stop isn't a time point, use an empty string value for the
							 * arrival_time and departure_time fields.
							 */
							else {
								Integer currentStopSequencePosition = Integer.parseInt(line[col.get(GtfsDefinitions.STOP_SEQUENCE)]);
								StopTime previousStopTime = trip.getStopTimes().get(currentStopSequencePosition-1);

								StopTime newStopTime = new StopTimeImpl(currentStopSequencePosition,
										previousStopTime.getArrivalTime(),
										previousStopTime.getDepartureTime(),
										line[col.get(GtfsDefinitions.STOP_ID)],
										line[col.get(GtfsDefinitions.TRIP_ID)]);

								trip.addStopTime(newStopTime);
								if(warnStopTimes) {
									log.warn("No arrival time set! Stops without arrival times will be scheduled based on the " +
											"nearest preceding timed stop. This message is only given once.");
									warnStopTimes = false;
								}
							}
						} catch (NumberFormatException | ParseException e) {
							e.printStackTrace();
						}
					}
				}
				line = reader.readNext();
			}

			reader.close();
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found in stop_times.txt");
		}
		log.info("...     stop_times.txt loaded");
	}

	/**
	 * Loads the frequencies (if available) and adds them to their respective trips in {@link #routes}.
	 * <p/>
	 * <br/><br/>
	 * frequencies.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Headway (time between trips) for routes with variable frequency of service.
	 */
	private void loadFrequencies() {
		log.info("Looking for frequencies.txt");
		// frequencies are optional
		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(root + GtfsDefinitions.Files.FREQUENCIES.fileName));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.FREQUENCIES.columns, GtfsDefinitions.Files.FREQUENCIES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				usesFrequencies = true;    // frequencies file might exists but could be empty

				for(Route actualGtfsRoute : routes.values()) {
					Trip trip = actualGtfsRoute.getTrips().get(line[col.get(GtfsDefinitions.TRIP_ID)]);
					if(trip != null) {
						try {
							trip.addFrequency(new FrequencyImpl(timeFormat.parse(line[col.get(GtfsDefinitions.START_TIME)]), timeFormat.parse(line[col.get(GtfsDefinitions.END_TIME)]), Integer.parseInt(line[col.get(GtfsDefinitions.HEADWAY_SECS)])));
						} catch (NumberFormatException | ParseException e) {
							e.printStackTrace();
						}
					}
				}
				line = reader.readNext();
			}
			reader.close();
			log.info("...     frequencies.txt loaded");
		} catch (FileNotFoundException e) {
			log.info("...     no frequencies file found.");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("Emtpy line found in frequencies.txt");
		}
	}

	/**
	 * @return a map which stores the service ids that run on each day
	 */
	private void calcDateStats() {
		for(Service service : services.values()) {
			Set<LocalDate> days = service.getCoveredDays();
			for(LocalDate day : days) {
				MapUtils.getSet(day, serviceDateStat).add(service);
				MapUtils.getSet(day, tripDateStat).addAll(service.getTrips().values());
			}
		}
	}

	@Override
	public Map<LocalDate, Set<Service>> getServicesOnDates() {
		return serviceDateStat;
	}

	@Override
	public Map<LocalDate, Set<Trip>> getTripsOnDates() {
		return tripDateStat;
	}

	@Override
	public Map<String, Stop> getStops() {
		return gtfsStops;
	}

	@Override
	public Map<String, Route> getRoutes() {
		return routes;
	}

	@Override
	public Map<Id<RouteShape>, RouteShape> getShapes() {
		return shapes;
	}

	@Override
	public boolean usesFrequencies() {
		return usesFrequencies;
	}

	@Override
	public Map<String, Service> getServices() {
		return services;
	}


}