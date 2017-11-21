/* *********************************************************************** *
 * project: org.matsim.*
 * GTFSDefinitions.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.gtfs.lib;

import java.time.format.DateTimeFormatter;

public final class GtfsDefinitions {

	public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_TIME;

	// column names
	public static final String SHAPE_ID = "shape_id";
	public static final String SHAPE_PT_LON = "shape_pt_lon";
	public static final String SHAPE_PT_LAT = "shape_pt_lat";
	public static final String SHAPE_PT_SEQUENCE = "shape_pt_sequence";
	public static final String STOP_LON = "stop_lon";
	public static final String STOP_LAT = "stop_lat";
	public static final String STOP_CODE = "stop_code";
	public static final String STOP_DESC = "stop_desc";
	public static final String ZONE_ID = "zone_id";
	public static final String STOP_URL = "stop_url";
	public static final String LOCATION_TYPE = "location_type";
	public static final String PARENT_STATION = "parent_station";
	public static final String STOP_TIMEZONE = "stop_timezone";
	public static final String WHEELCHAIR_BOARDING = "wheelchair_boarding";

	public static final String STOP_NAME = "stop_name";
	public static final String STOP_ID = "stop_id";
	public static final String SERVICE_ID = "service_id";
	public static final String START_DATE = "start_date";
	public static final String END_DATE = "end_date";
	public static final String EXCEPTION_TYPE = "exception_type";
	public static final String DATE = "date";
	public static final String ROUTE_SHORT_NAME = "route_short_name";
	public static final String ROUTE_TYPE = "route_type";
	public static final String ROUTE_ID = "route_id";
	public static final String TRIP_ID = "trip_id";
	public static final String STOP_SEQUENCE = "stop_sequence";
	public static final String ARRIVAL_TIME = "arrival_time";
	public static final String DEPARTURE_TIME = "departure_time";
	public static final String START_TIME = "start_time";
	public static final String END_TIME = "end_time";
	public static final String HEADWAY_SECS = "headway_secs";

	public static final String MONDAY = "monday";
	public static final String TUESDAY = "tuesday";
	public static final String WEDNESDAY = "wednesday";
	public static final String THURSDAY = "thursday";
	public static final String FRIDAY = "friday";
	public static final String SATURDAY = "saturday";
	public static final String SUNDAY = "sunday";

	public static final String EXACT_TIMES = "exact_times";

	public static final String ROUTE_DESC = "route_desc";
	public static final String ROUTE_URL = "route_url";
	public static final String ROUTE_COLOR = "route_color";
	public static final String ROUTE_TEXT_COLOR = "route_text_color";

	public static final String SHAPE_DIST_TRAVELED = "shape_dist_traveled";

	public static final String TRIP_HEADSIGN = "trip_headsign";
	public static final String TRIP_SHORT_NAME = "trip_short_name";
	public static final String DIRECTION_ID = "direction_id";
	public static final String BLOCK_ID = "block_id";
	public static final String WHEELCHAIR_ACCESSIBLE = "wheelchair_accessible";
	public static final String BIKES_ALLOWED = "bikes_allowed";

	public static final String PICKUP_TYPE = "pickup_type";
	public static final String DROP_OFF_TYPE = "drop_off_type";
	public static final String TIMEPOINT = "timepoint";

	public static final String FROM_STOP_ID = "from_stop_id";
	public static final String TO_STOP_ID = "to_stop_id";
	public static final String TRANSFER_TYPE = "transfer_type";
	public static final String MIN_TRANSFER_TIME = "min_transfer_time";


	/**
	 * Values
	 */
	public enum Files {
		STOPS("Stop", "stops.txt",
				new String[]{STOP_ID, STOP_LON, STOP_LAT, STOP_NAME},
				new String[]{STOP_CODE, STOP_DESC, ZONE_ID, STOP_URL, LOCATION_TYPE, PARENT_STATION, STOP_TIMEZONE, WHEELCHAIR_BOARDING}),

		CALENDAR("Calendar",
				"calendar.txt",
				new String[]{SERVICE_ID, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY, START_DATE, END_DATE},
				new String[]{}),

		CALENDAR_DATES("CalendarDates",
				"calendar_dates.txt",
				new String[]{SERVICE_ID, DATE, EXCEPTION_TYPE},
				new String[]{}),

		SHAPES("Shape",
				"shapes.txt",
				new String[]{SHAPE_ID, SHAPE_PT_LON, SHAPE_PT_LAT, SHAPE_PT_SEQUENCE},
				new String[]{SHAPE_DIST_TRAVELED}),

		ROUTES("Route",
				"routes.txt",
				new String[]{ROUTE_ID, ROUTE_SHORT_NAME, ROUTE_TYPE},
				new String[]{ROUTE_DESC, ROUTE_URL, ROUTE_COLOR, ROUTE_TEXT_COLOR}),

		TRIPS("Trip",
				"trips.txt",
				new String[]{ROUTE_ID, TRIP_ID, SERVICE_ID},
				new String[]{TRIP_HEADSIGN, TRIP_SHORT_NAME, DIRECTION_ID, BLOCK_ID, SHAPE_ID, WHEELCHAIR_ACCESSIBLE, BIKES_ALLOWED}),

		STOP_TIMES("StopTime",
				"stop_times.txt",
				new String[]{TRIP_ID, STOP_SEQUENCE, ARRIVAL_TIME, DEPARTURE_TIME, STOP_ID},
				new String[]{PICKUP_TYPE, DROP_OFF_TYPE, TIMEPOINT}),

		FREQUENCIES("Frequency",
				"frequencies.txt",
				new String[]{TRIP_ID, START_TIME, END_TIME, HEADWAY_SECS},
				new String[]{EXACT_TIMES}),

		TRANSFERS("Transfer",
				"transfers.txt",
				new String[]{FROM_STOP_ID, TO_STOP_ID, TRANSFER_TYPE},
				new String[]{MIN_TRANSFER_TIME});

		public final String name;
		public final String fileName;
		public final String[] columns;
		public final String[] optionalColumns;

		Files(String name, String fileName, String[] requiredColumns, String[] optionalColumns) {
			this.name = name;
			this.fileName = fileName;
			this.columns = requiredColumns;
			this.optionalColumns = optionalColumns;
		}
	}

	/**
	 * [https://developers.google.com/transit/gtfs/reference/#routestxt]<br/>
	 * The route_type field describes the type of transportation used on a route.
	 */
	public enum RouteType {
		/**
		 * Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area.
		 */
		TRAM(0, "tram"),
		/**
		 * Subway, Metro. Any underground rail system within a metropolitan area.
		 */
		SUBWAY(1, "subway"),
		/**
		 * Rail. Used for intercity or long-distance travel.
		 */
		RAIL(2, "rail"),
		/**
		 * Bus. Used for short- and long-distance bus routes.
		 */
		BUS(3, "bus"),
		/**
		 * Ferry. Used for short- and long-distance boat service.
		 */
		FERRY(4, "ferry"),
		/**
		 * Cable car. Used for street-level cable cars where the cable runs beneath the car.
		 */
		CABLE_CAR(5, "cable car"),
		/**
		 * Gondola, Suspended cable car. Typically used for aerial cable cars where the car is suspended from the cable.
		 */
		GONDOLA(6, "gondola"),
		/**
		 * Funicular. Any rail system designed for steep inclines.
		 */
		FUNICULAR(7, "funicular");

		public int index;
		public String name;

		RouteType(int index, String name) {
			this.index = index;
			this.name = name;
		}

		/**
		 * Determines the route type of a given route type index
		 *
		 * - Standard: https://developers.google.com/transit/gtfs/reference/routes-file
		 * - Extended: https://developers.google.com/transit/gtfs/reference/extended-route-types
		 */
		public static RouteType getRouteType(int routeType) {
			if(routeType < RouteType.values().length) {
				return RouteType.values()[routeType];
			}

			// Extended route types
			switch (routeType  - (routeType % 100)) {
				case 100: return RouteType.RAIL; // Railway Service
				case 200: return RouteType.BUS; // Coach Service
				case 300: return RouteType.RAIL; // Suburban Railway Service
				case 400: return RouteType.RAIL; // Urban Railway Service
				case 500: return RouteType.SUBWAY; // Metro Service
				case 600: return RouteType.SUBWAY; // Underground Service
				case 700: return RouteType.BUS; // Bus Service
				case 800: return RouteType.BUS; // Trolleybus Service
				case 900: return RouteType.TRAM; // Tram Service
				case 1000: return RouteType.FERRY; // Water Transport Service
				case 1200: return RouteType.FERRY; // Ferry Service
				case 1300: return RouteType.CABLE_CAR; // Telecabin Service
				case 1400: return RouteType.FUNICULAR; // Funicular Service

				case 1100: // Air Service
				case 1500: // Taxi Service
				case 1600: // Self Drive
				case 1700: // Miscellaneous Service
				return null;
			}
			throw new IllegalArgumentException("Invalid GTFS route type: " + routeType);
		}
	}

	/**
	 * <i>[https://developers.google.com/transit/gtfs/reference/#stopstxt]</i><br/>
	 * Identifies whether this stop ID represents a stop or station. If no location type is specified, or the
	 * location_type is blank, stop IDs are treated as stops. Stations can have different properties from stops when
	 * they are represented on a map or used in trip planning.<br/><br/>
	 * <p>
	 * The location type field can have the following values:<br/>
	 * 0 or blank: Stop. A location where passengers board or disembark from a transit vehicle<br/>
	 * 1: Station. A physical structure or area that contains one or more stop<br/>
	 */
	public enum LocationType {
		STOP, STATION
	}

	public enum FareTransferType {
		NO_TRANSFER_PERMITTED, TRANSFER_ONCE, TRANSFER_TWICE, UNLIMITED
	}

	public enum PaymentMethod {
		ON_BOARD, BEFORE_BOARDING
	}

	/**
	 * [https://developers.google.com/transit/gtfs/reference/#transferstxt]
	 * Optional
	 */
	public enum TransferType {
		RECOMMENDED_TRANSFER_POINT(0, "Recommended transfer point"), // This is a recommended transfer point between routes.
		TIMED_TRANSFER_POINT(1, "Timed transfer point"), //This is a timed transfer point between two routes. The departing vehicle is expected to wait for the arriving one, with sufficient time for a passenger to transfer between routes.
		REQUIRES_MIN_TRANSFER_TIME(2, "Requires minimal transfer time"), // This transfer requires a minimum amount of time between arrival and departure to ensure a connection. The time required to transfer is specified by min_transfer_time.
		TRANSFER_NOT_POSSIBLE(3, "Transfer not possible");

		public int index;
		public String name;

		TransferType(int index, String name) {
			this.index = index;
			this.name = name;
		}
	}
}
