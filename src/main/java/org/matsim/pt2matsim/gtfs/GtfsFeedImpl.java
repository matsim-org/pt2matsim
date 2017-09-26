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
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions.RouteTypes;
import org.matsim.pt2matsim.lib.RouteShape;
import org.apache.commons.io.input.BOMInputStream;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

	// containers for storing gtfs data
	private Map<String, Stop> stops = new HashMap<>();
	private Map<String, Route> routes = new TreeMap<>();
	private Map<String, Service> services = new HashMap<>();
	private Map<String, Trip> trips = new HashMap<>();
	private Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();
	private String coordSys = TransformationFactory.WGS84;

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
		log.info("All files loaded");
	}
	
	/**
	 * Creates a reader for CSV files
	 * 
	 * GTFS allows a BOM to precede the file content, which needs to be skipped
	 * in case it is present

	 * @throws FileNotFoundException
	 */
	private CSVReader createCSVReader(String path) throws FileNotFoundException {
		InputStream stream = new BOMInputStream(new FileInputStream(path));
		return new CSVReader(new InputStreamReader(stream));
	}

	/**
	 * Reads all stops and puts them in {@link #stops}
	 * <p/>
	 * <br/><br/>
	 * stops.txt <i>[https://developers.google.com/transit/gtfs/reference]</i><br/>
	 * Individual locations where vehicles pick up or drop off passengers.
	 *
	 * @throws IOException
	 */
	private void loadStops() throws IOException {
		log.info("Loading stops.txt");
		
		try {
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.STOPS.fileName);
			
			String[] header = reader.readNext(); // read header
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.STOPS.columns, GtfsDefinitions.Files.STOPS.optionalColumns); // get column numbers for required fields

			String[] line = reader.readNext();
			while(line != null) {
				String stopId = line[col.get(GtfsDefinitions.STOP_ID)];
				Stop stop = new StopImpl(stopId, line[col.get(GtfsDefinitions.STOP_NAME)], Double.parseDouble(line[col.get(GtfsDefinitions.STOP_LON)]), Double.parseDouble(line[col.get(GtfsDefinitions.STOP_LAT)]));
				stops.put(stopId, stop);

				// location type
				if(col.get(GtfsDefinitions.LOCATION_TYPE) != null) {
					if(line[col.get(GtfsDefinitions.LOCATION_TYPE)].equals("0")) {
						((StopImpl) stop).setLocationType(GtfsDefinitions.LocationType.STOP);
					}
					if(line[col.get(GtfsDefinitions.LOCATION_TYPE)].equals("1")) {
						((StopImpl) stop).setLocationType(GtfsDefinitions.LocationType.STATION);
					}
				}

				// parent station
				if(col.get(GtfsDefinitions.PARENT_STATION) != null && !line[col.get(GtfsDefinitions.PARENT_STATION)].isEmpty()) {
					((StopImpl) stop).setParentStation(line[col.get(GtfsDefinitions.PARENT_STATION)]);
				}

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
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.CALENDAR.fileName);

			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.CALENDAR.columns, GtfsDefinitions.Files.CALENDAR.optionalColumns);

			// assuming all days really do follow monday in the file
			int indexMonday = col.get(GtfsDefinitions.MONDAY);

			String[] line = reader.readNext();
			while(line != null) {
				// if(i == Math.pow(2, c)) { log.info("        # " + i); c++; } i++;

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
		
		try {
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.CALENDAR_DATES.fileName);
			
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
					((ServiceImpl) currentService).addException(line[col.get(GtfsDefinitions.DATE)]);
				} else {
					((ServiceImpl) currentService).addAddition(line[col.get(GtfsDefinitions.DATE)]);
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
		
		try {
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.SHAPES.fileName);

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
	 * Determines the route type of a given route type index
	 * 
	 * - Standard: https://developers.google.com/transit/gtfs/reference/routes-file
	 * - Extended: https://developers.google.com/transit/gtfs/reference/extended-route-types
	 */
	private RouteTypes getRouteType(int routeType) {
		// Standard route types
		switch (routeType) {
		case 0: return RouteTypes.TRAM;
		case 1: return RouteTypes.SUBWAY;
		case 2: return RouteTypes.RAIL;
		case 3: return RouteTypes.BUS;
		case 4: return RouteTypes.FERRY;
		case 5: return RouteTypes.CABLE_CAR;
		case 6: return RouteTypes.GONDOLA;
		case 7: return RouteTypes.FUNICULAR;
		}
		
		// Extended route types
		switch (routeType / 100) {
		case 1: return RouteTypes.RAIL; // Railway Service
		case 2: return RouteTypes.BUS; // Coach Service
		case 3: return RouteTypes.RAIL; // Suburban Railway Service
		case 4: return RouteTypes.RAIL; // Urban Railway Service
		case 5: return RouteTypes.SUBWAY; // Metro Service 
		case 6: return RouteTypes.SUBWAY; // Underground Service
		case 7: return RouteTypes.BUS; // Bus Service
		case 8: return RouteTypes.BUS; // Trolleybus Service
		case 9: return RouteTypes.TRAM; // Tram Service
		case 10: return RouteTypes.FERRY; // Water Transport Service
		case 12: return RouteTypes.FERRY; // Ferry Service
		case 13: return RouteTypes.CABLE_CAR; // Telecabin Service
		case 14: return RouteTypes.FUNICULAR; // Funicular Service
		
		case 11: // Air Service
		case 15: // Taxi Service
		case 16: // Self Drive
		case 17: // Miscellaneous Service
			return null;
		}
		
		throw new IllegalArgumentException("Invalid GTFS route type: " + routeType);
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
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.ROUTES.fileName);
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.ROUTES.columns, GtfsDefinitions.Files.ROUTES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				int routeTypeNr = Integer.parseInt(line[col.get(GtfsDefinitions.ROUTE_TYPE)]);
				RouteTypes routeType = getRouteType(routeTypeNr);

				if (routeType == null) {
					log.warn("Route of type " + routeType + " will be ignored");
				} else {
					Route newGtfsRoute = new RouteImpl(line[col.get(GtfsDefinitions.ROUTE_ID)], line[col.get(GtfsDefinitions.ROUTE_SHORT_NAME)], GtfsDefinitions.RouteTypes.values()[routeTypeNr]);
					routes.put(line[col.get(GtfsDefinitions.ROUTE_ID)], newGtfsRoute);
				}

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
	 * Trips for each route. A trip is a sequence of two or more stops that occurs at specific time.
	 *
	 * @throws IOException
	 */
	private void loadTrips() throws IOException {
		log.info("Loading trips.txt");
		try {
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.TRIPS.fileName);
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.TRIPS.columns, GtfsDefinitions.Files.TRIPS.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				Trip newTrip;
				Route route = routes.get(line[col.get(GtfsDefinitions.ROUTE_ID)]);
				Service service = services.get(line[col.get(GtfsDefinitions.SERVICE_ID)]);

				if(usesShapes) {
					Id<RouteShape> shapeId = Id.create(line[col.get(GtfsDefinitions.SHAPE_ID)], RouteShape.class); // column might not be available
					newTrip = new TripImpl(line[col.get(GtfsDefinitions.TRIP_ID)], route, service, shapes.get(shapeId));
				} else {
					newTrip = new TripImpl(line[col.get(GtfsDefinitions.TRIP_ID)], route, service, null);
				}

				// store Trip
				((RouteImpl) route).addTrip(newTrip);
				((ServiceImpl) service).addTrip(newTrip);
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
	 * Times that a vehicle arrives at and departs from individual stops for each trip.
	 *
	 * @throws IOException
	 */
	private void loadStopTimes() throws IOException {
		log.info("Loading stop_times.txt");
		try {
			boolean warnStopTimes = true;
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.STOP_TIMES.fileName);
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.STOP_TIMES.columns, GtfsDefinitions.Files.STOP_TIMES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {

				Trip trip = trips.get(line[col.get(GtfsDefinitions.TRIP_ID)]);
				Stop stop = stops.get(line[col.get(GtfsDefinitions.STOP_ID)]);

				if(!line[col.get(GtfsDefinitions.ARRIVAL_TIME)].equals("")) {
					// get position and times
					int sequencePosition = Integer.parseInt(line[col.get(GtfsDefinitions.STOP_SEQUENCE)]);
					int arrivalTime = (int) Time.parseTime(line[col.get(GtfsDefinitions.ARRIVAL_TIME)]);
					int departureTime = (int) Time.parseTime(line[col.get(GtfsDefinitions.DEPARTURE_TIME)]);

					// create StopTime
					StopTime newStopTime = new StopTimeImpl(sequencePosition,
							arrivalTime,
							departureTime,
							stop,
							trip
					);
					((TripImpl) trip).addStopTime(newStopTime);

					// add trip to stop
					((StopImpl) stop).addTrip(trip);
				}
				/* GTFS Reference: If this stop isn't a time point, use an empty string value for the
				  arrival_time and departure_time fields.
				 */
				else {
					Integer currentStopSequencePosition = Integer.parseInt(line[col.get(GtfsDefinitions.STOP_SEQUENCE)]);
					StopTime previousStopTime = trip.getStopTimes().last();

					// create StopTime
					StopTime newStopTime = new StopTimeImpl(currentStopSequencePosition,
							previousStopTime.getArrivalTime(),
							previousStopTime.getDepartureTime(),
							stop,
							trip);

					((TripImpl) trip).addStopTime(newStopTime);

					// add trip to stop
					((StopImpl) stop).addTrip(trip);

					if(warnStopTimes) {
						log.warn("No arrival time set! Stops without arrival times will be scheduled based on the " +
								"nearest preceding timed stop. This message is only given once.");
						warnStopTimes = false;
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
		try {
			CSVReader reader = createCSVReader(root + GtfsDefinitions.Files.FREQUENCIES.fileName);
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.FREQUENCIES.columns, GtfsDefinitions.Files.FREQUENCIES.optionalColumns);

			String[] line = reader.readNext();
			while(line != null) {
				usesFrequencies = true;    // frequencies file might exists but could be empty

				for(Route actualGtfsRoute : routes.values()) {
					Trip trip = actualGtfsRoute.getTrips().get(line[col.get(GtfsDefinitions.TRIP_ID)]);
					if(trip != null) {
						try {
							Frequency newFreq = new FrequencyImpl(
									(int) Time.parseTime(line[col.get(GtfsDefinitions.START_TIME)]),
									(int) Time.parseTime(line[col.get(GtfsDefinitions.END_TIME)]),
									Integer.parseInt(line[col.get(GtfsDefinitions.HEADWAY_SECS)]));
							((TripImpl) trip).addFrequency(newFreq);
						} catch (NumberFormatException e) {
							e.printStackTrace();
						}
					}
				}
				line = reader.readNext();
			}
			reader.close();
			if(usesFrequencies) {
				log.info("...     frequencies.txt loaded");
			} else {
				log.info("...     frequencies.txt has no entries");
			}
		} catch (FileNotFoundException e) {
			log.info("...     no frequencies file found");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("Emtpy line found in frequencies.txt");
		}
	}

	@Override
	public Map<String, Stop> getStops() {
		return stops;
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
	public double[] transform(String targetCoordinateSystem) {
		double minE = Double.MAX_VALUE, minN = Double.MAX_VALUE, maxE = Double.MIN_VALUE, maxN = Double.MIN_VALUE;

		CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(coordSys, targetCoordinateSystem);
		for(Stop stop : stops.values()) {
			((StopImpl) stop).setCoord(transformation.transform(stop.getCoord()));

			if(stop.getCoord().getX() > maxE) maxE = stop.getCoord().getX();
			if(stop.getCoord().getY() > maxN) maxN = stop.getCoord().getY();
			if(stop.getCoord().getX() < minE) minE = stop.getCoord().getX();
			if(stop.getCoord().getY() < minN) minN = stop.getCoord().getY();
		}
		this.coordSys = targetCoordinateSystem;
		return new double[]{minE, minN, maxE, maxN};
	}

	@Override
	public String getCurrentCoordSystem() {
		return coordSys;
	}

	@Override
	public Map<String, Service> getServices() {
		return services;
	}

	@Override
	public Map<String, Trip> getTrips() {
		return trips;
	}


}