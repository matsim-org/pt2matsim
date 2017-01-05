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

public final class GtfsDefinitions {

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


	//Constants
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
				new String[]{TRIP_ID, STOP_SEQUENCE, ARRIVAL_TIME, DEPARTURE_TIME, STOP_ID}),

		FREQUENCIES("Frequency",
				"frequencies.txt",
				new String[]{TRIP_ID, START_TIME, END_TIME, HEADWAY_SECS},
				new String[]{EXACT_TIMES});

		//Attributes
		public final String name;
		public final String fileName;
		public final String[] columns;
		public final String[] optionalColumns;

		//Methods
		Files(String name, String fileName, String[] requiredColumns, String[] optionalColumns) {
			this.name = name;
			this.fileName = fileName;
			this.columns = requiredColumns;
			this.optionalColumns = optionalColumns;
		}
	}


	
	public enum WayTypes {
		RAIL,
		ROAD,
		WATER,
		CABLE
	}
	public enum RouteTypes {
		//Values
		TRAM("tram", WayTypes.RAIL),
		SUBWAY("subway", WayTypes.RAIL),
		RAIL("rail", WayTypes.RAIL),
		BUS("bus", WayTypes.ROAD),
		FERRY("ferry", WayTypes.WATER),
		CABLE_CAR("cable car", WayTypes.CABLE),
		GONDOLA("gondola", WayTypes.CABLE),
		FUNICULAR("funicular", WayTypes.RAIL);
		//Attributes
		public String name;
		public WayTypes wayType;
		//Methods
		RouteTypes(String name,WayTypes wayType) {
			this.name = name;
			this.wayType = wayType;
		}
	}

}
