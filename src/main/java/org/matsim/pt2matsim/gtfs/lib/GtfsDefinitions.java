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

import java.util.Map;
import java.util.TreeMap;

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
	public static final String ROUTE_LONG_NAME = "route_long_name";
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
				new String[]{ROUTE_ID, ROUTE_SHORT_NAME, ROUTE_LONG_NAME, ROUTE_TYPE},
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
		FUNICULAR(7, "funicular"),
		/**
		 * Non-standard route type for extended route services like air services, bike, miscellaneous etc.
		 */
		OTHER(9999, "other");

		public int index;
		public String name;

		RouteType(int index, String name) {
			this.index = index;
			this.name = name;
		}

		/**
		 * Determines the route type of a given route type index
		 */
		public static ExtendedRouteType getExtendedRouteType(int routeType) {
			return ExtendedRouteType.getExtendedRouteType(routeType);
		}
	}

	public enum ExtendedRouteType {
		// Base: https://developers.google.com/transit/gtfs/reference/routes-file
		Tram      (0, "Tram", RouteType.TRAM),
		Subway    (1, "Subway", RouteType.SUBWAY),
		Rail      (2, "Rail", RouteType.RAIL),
		Bus       (3, "Bus", RouteType.BUS),
		Ferry     (4, "Ferry", RouteType.FERRY),
		Cable_car (5, "Cable car", RouteType.CABLE_CAR),
		Gondola   (6, "Gondola", RouteType.GONDOLA),
		Funicular (7, "Funicular", RouteType.FUNICULAR),

		// Extended: https://developers.google.com/transit/gtfs/reference/extended-route-types
		Railway_Service                       (100,  "Railway Service", 				RouteType.RAIL),
		High_Speed_Rail_Service               (101,  "High Speed Rail Service", 		RouteType.RAIL),
		Long_Distance_Trains                  (102,  "Long Distance Trains", 			RouteType.RAIL),
		Inter_Regional_Rail_Service           (103,  "Inter Regional Rail Service",		RouteType.RAIL),
		Car_Transport_Rail_Service            (104,  "Car Transport Rail Service", 		RouteType.RAIL),
		Sleeper_Rail_Service                  (105,  "Sleeper Rail Service", 			RouteType.RAIL),
		Regional_Rail_Service                 (106,  "Regional Rail Service", 			RouteType.RAIL),
		Tourist_Railway_Service               (107,  "Tourist Railway Service", 		RouteType.RAIL),
		Rail_Shuttle_Within_Complex           (108,  "Rail Shuttle (Within Complex)", 	RouteType.RAIL),
		Suburban_Railway                      (109,  "Suburban Railway", 				RouteType.RAIL),
		Replacement_Rail_Service              (110,  "Replacement Rail Service", 		RouteType.RAIL),
		Special_Rail_Service                  (111,  "Special Rail Service", 			RouteType.RAIL),
		Lorry_Transport_Rail_Service          (112,  "Lorry Transport Rail Service",	RouteType.RAIL),
		All_Rail_Services                     (113,  "All Rail Services",				RouteType.RAIL),
		Cross_Country_Rail_Service            (114,  "Cross-Country Rail Service",		RouteType.RAIL),
		Vehicle_Transport_Rail_Service        (115,  "Vehicle Transport Rail Service",	RouteType.RAIL),
		Rack_and_Pinion_Railway               (116,  "Rack and Pinion Railway",			RouteType.RAIL),
		Additional_Rail_Service               (117,  "Additional Rail Service", 		RouteType.RAIL),

		Coach_Service                         (200,  "Coach Service",					RouteType.RAIL),
		International_Coach_Service           (201,  "International Coach Service",		RouteType.BUS),
		National_Coach_Service                (202,  "National Coach Service",			RouteType.BUS),
		Shuttle_Coach_Service                 (203,  "Shuttle Coach Service",			RouteType.BUS),
		Regional_Coach_Service                (204,  "Regional Coach Service",			RouteType.BUS),
		Special_Coach_Service                 (205,  "Special Coach Service",			RouteType.BUS),
		Sightseeing_Coach_Service             (206,  "Sightseeing Coach Service",		RouteType.BUS),
		Tourist_Coach_Service                 (207,  "Tourist Coach Service",			RouteType.BUS),
		Commuter_Coach_Service                (208,  "Commuter Coach Service",			RouteType.BUS),
		All_Coach_Services                    (209,  "All Coach Services",				RouteType.BUS),

		Suburban_Railway_Service              (300,  "Suburban Railway Service",		RouteType.RAIL),

		Urban_Railway_Service                 (400,  "Urban Railway Service", 			RouteType.RAIL),
		Metro_Service_2                       (401,  "Metro Service",					RouteType.SUBWAY),
		Underground_Service_2                 (402,  "Underground Service",				RouteType.SUBWAY),
		Urban_Railway_Service_2               (403,  "Urban Railway Service",			RouteType.RAIL),
		All_Urban_Railway_Services            (404,  "All Urban Railway Services",		RouteType.RAIL),
		Monorail                              (405,  "Monorail",						RouteType.RAIL),

		Metro_Service                         (500,  "Metro Service", 					RouteType.SUBWAY),

		Underground_Service                   (600,  "Underground Service", 			RouteType.SUBWAY),

		Bus_Service                           (700,  "Bus Service", 					RouteType.BUS),
		Regional_Bus_Service                  (701,  "Regional Bus Service", 			RouteType.BUS),
		Express_Bus_Service                   (702,  "Express Bus Service", 			RouteType.BUS),
		Stopping_Bus_Service                  (703,  "Stopping Bus Service", 			RouteType.BUS),
		Local_Bus_Service                     (704,  "Local Bus Service", 				RouteType.BUS),
		Night_Bus_Service                     (705,  "Night Bus Service", 				RouteType.BUS),
		Post_Bus_Service                      (706,  "Post Bus Service", 				RouteType.BUS),
		Special_Needs_Bus                     (707,  "Special Needs Bus", 				RouteType.BUS),
		Mobility_Bus_Service                  (708,  "Mobility Bus Service", 			RouteType.BUS),
		Mobility_Bus_for_Registered_Disabled  (709,  "Mobility Bus for Registered Disabled", RouteType.BUS),
		Sightseeing_Bus                       (710,  "Sightseeing Bus",					RouteType.BUS),
		Shuttle_Bus                           (711,  "Shuttle Bus",						RouteType.BUS),
		School_Bus                            (712,  "School Bus",						RouteType.BUS),
		School_and_Public_Service_Bus         (713,  "School and Public Service Bus", 	RouteType.BUS),
		Rail_Replacement_Bus_Service          (714,  "Rail Replacement Bus Service", 	RouteType.BUS),
		Demand_and_Response_Bus_Service       (715,  "Demand and Response Bus Service",	RouteType.BUS),
		All_Bus_Services                      (716,  "All Bus Services",				RouteType.BUS),

		Trolleybus_Service                    (800,  "Trolleybus Service",				RouteType.BUS),

		Tram_Service                          (900,  "Tram Service", 					RouteType.TRAM),
		City_Tram_Service                     (901,  "City Tram Service", 				RouteType.TRAM),
		Local_Tram_Service                    (902,  "Local Tram Service", 				RouteType.TRAM),
		Regional_Tram_Service                 (903,  "Regional Tram Service", 			RouteType.TRAM),
		Sightseeing_Tram_Service              (904,  "Sightseeing Tram Service", 		RouteType.TRAM),
		Shuttle_Tram_Service                  (905,  "Shuttle Tram Service", 			RouteType.TRAM),
		All_Tram_Services                     (906,  "All Tram Services", 				RouteType.TRAM),

		Water_Transport_Service               (1000, "Water Transport Service", 			RouteType.FERRY),
		International_Car_Ferry_Service       (1001, "International Car Ferry Service", 	RouteType.FERRY),
		National_Car_Ferry_Service            (1002, "National Car Ferry Service",			RouteType.FERRY),
		Regional_Car_Ferry_Service            (1003, "Regional Car Ferry Service", 			RouteType.FERRY),
		Local_Car_Ferry_Service               (1004, "Local Car Ferry Service", 			RouteType.FERRY),
		International_Passenger_Ferry_Service (1005, "International Passenger Ferry Service", RouteType.FERRY),
		National_Passenger_Ferry_Service      (1006, "National Passenger Ferry Service",	RouteType.FERRY),
		Regional_Passenger_Ferry_Service      (1007, "Regional Passenger Ferry Service",	RouteType.FERRY),
		Local_Passenger_Ferry_Service         (1008, "Local Passenger Ferry Service", 		RouteType.FERRY),
		Post_Boat_Service                     (1009, "Post Boat Service", 					RouteType.FERRY),
		Train_Ferry_Service                   (1010, "Train Ferry Service", 				RouteType.FERRY),
		Road_Link_Ferry_Service               (1011, "Road-Link Ferry Service", 			RouteType.FERRY),
		Airport_Link_Ferry_Service            (1012, "Airport-Link Ferry Service", 			RouteType.FERRY),
		Car_High_Speed_Ferry_Service          (1013, "Car High-Speed Ferry Service", 		RouteType.FERRY),
		Passenger_High_Speed_Ferry_Service    (1014, "Passenger High-Speed Ferry Service",	RouteType.FERRY),
		Sightseeing_Boat_Service              (1015, "Sightseeing Boat Service", 			RouteType.FERRY),
		School_Boat                           (1016, "School Boat",							RouteType.FERRY),
		Cable_Drawn_Boat_Service              (1017, "Cable-Drawn Boat Service",			RouteType.FERRY),
		River_Bus_Service                     (1018, "River Bus Service",					RouteType.FERRY),
		Scheduled_Ferry_Service               (1019, "Scheduled Ferry Service", 			RouteType.FERRY),
		Shuttle_Ferry_Service                 (1020, "Shuttle Ferry Service",				RouteType.FERRY),
		All_Water_Transport_Services          (1021, "All Water Transport Services", 		RouteType.FERRY),

		Air_Service                           (1100, "Air Service", 						RouteType.OTHER),
		International_Air_Service             (1101, "International Air Service", 			RouteType.OTHER),
		Domestic_Air_Service                  (1102, "Domestic Air Service", 				RouteType.OTHER),
		Intercontinental_Air_Service          (1103, "Intercontinental Air Service", 		RouteType.OTHER),
		Domestic_Scheduled_Air_Service        (1104, "Domestic Scheduled Air Service", 		RouteType.OTHER),
		Shuttle_Air_Service                   (1105, "Shuttle Air Service", 				RouteType.OTHER),
		Intercontinental_Charter_Air_Service  (1106, "Intercontinental Charter Air Service",RouteType.OTHER),
		International_Charter_Air_Service     (1107, "International Charter Air Service", 	RouteType.OTHER),
		Round_Trip_Charter_Air_Service        (1108, "Round-Trip Charter Air Service", 		RouteType.OTHER),
		Sightseeing_Air_Service               (1109, "Sightseeing Air Service", 			RouteType.OTHER),
		Helicopter_Air_Service                (1110, "Helicopter Air Service", 				RouteType.OTHER),
		Domestic_Charter_Air_Service          (1111, "Domestic Charter Air Service",		RouteType.OTHER),
		Schengen_Area_Air_Service             (1112, "Schengen-Area Air Service", 			RouteType.OTHER),
		Airship_Service                       (1113, "Airship Service", 					RouteType.OTHER),
		All_Air_Services                      (1114, "All Air Services", 					RouteType.OTHER),

		Ferry_Service                         (1200, "Ferry Service", 				RouteType.FERRY),

		Telecabin_Service                     (1300, "Telecabin Service", 			RouteType.CABLE_CAR),
		Telecabin_Service_2                   (1301, "Telecabin Service", 			RouteType.CABLE_CAR),
		Cable_Car_Service                     (1302, "Cable Car Service", 			RouteType.CABLE_CAR),
		Elevator_Service                      (1303, "Elevator Service", 			RouteType.CABLE_CAR),
		Chair_Lift_Service                    (1304, "Chair Lift Service", 			RouteType.CABLE_CAR),
		Drag_Lift_Service                     (1305, "Drag Lift Service", 			RouteType.CABLE_CAR),
		Small_Telecabin_Service               (1306, "Small Telecabin Service", 	RouteType.CABLE_CAR),
		All_Telecabin_Services                (1307, "All Telecabin Services", 		RouteType.CABLE_CAR),

		Funicular_Service_                    (1400, "Funicular Service", 			RouteType.FUNICULAR),
		Funicular_Service                     (1401, "Funicular Service", 			RouteType.FUNICULAR),
		All_Funicular_Service                 (1402, "All Funicular Service", 		RouteType.FUNICULAR),

		Taxi_Service                          (1500, "Taxi Service", 				RouteType.OTHER),
		Communal_Taxi_Service                 (1501, "Communal Taxi Service", 		RouteType.BUS),
		Water_Taxi_Service                    (1502, "Water Taxi Service", 			RouteType.FERRY),
		Rail_Taxi_Service                     (1503, "Rail Taxi Service", 			RouteType.RAIL),
		Bike_Taxi_Service                     (1504, "Bike Taxi Service", 			RouteType.OTHER),
		Licensed_Taxi_Service                 (1505, "Licensed Taxi Service", 		RouteType.OTHER),
		Private_Hire_Service_Vehicle          (1506, "Private Hire Service Vehicle",RouteType.OTHER),
		All_Taxi_Services                     (1507, "All Taxi Services", 			RouteType.OTHER),

		Self_Drive                            (1600, "Self Drive", 		RouteType.OTHER),
		Hire_Car                              (1601, "Hire Car", 		RouteType.OTHER),
		Hire_Van                              (1602, "Hire Van", 		RouteType.OTHER),
		Hire_Motorbike                        (1603, "Hire Motorbike", 	RouteType.OTHER),
		Hire_Cycle                            (1604, "Hire Cycle", 		RouteType.OTHER),

		Miscellaneous_Service                 (1700, "Miscellaneous Service", 	RouteType.OTHER),
		Horse_drawn_Carriage                  (1701, "Horse-drawn Carriage", 	RouteType.OTHER);

		public final int index;
		public final String name;
		public final RouteType routeType;

		ExtendedRouteType(int index, String name, RouteType routeType) {
			this.index = index;
			this.name = name;
			this.routeType = routeType;
		}

		private static final Map<Integer, ExtendedRouteType> extendedRouteTypes = new TreeMap<>();

		static {
			for(ExtendedRouteType type : ExtendedRouteType.values()) {
				extendedRouteTypes.put(type.index, type);

			}
		}

		public static ExtendedRouteType getExtendedRouteType(int routeType) {
			ExtendedRouteType extRouteType = extendedRouteTypes.get(routeType);
			if(extRouteType == null) {
				throw new IllegalArgumentException("Invalid GTFS route type: " + routeType);
			}
			return extendedRouteTypes.get(routeType);
		}

		public static ExtendedRouteType getExtendedRouteType(RouteType routeType) {
			return getExtendedRouteType(routeType.index);
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
		TIMED_TRANSFER_POINT(1, "Timed transfer point"), // This is a timed transfer point between two routes. The departing vehicle is expected to wait for the arriving one, with sufficient time for a passenger to transfer between routes.
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
