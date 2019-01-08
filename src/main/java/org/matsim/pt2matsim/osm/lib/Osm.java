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

package org.matsim.pt2matsim.osm.lib;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;

import com.google.common.base.Joiner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Definitions for OpenStreetMap (OSM) including interfaces for elements
 *
 * @author polettif
 */
public final class Osm {

	/**
	 * OSM tags used by the converters
	 */
	public enum ElementType {
		NODE("node"),
		WAY("way"),
		RELATION("relation");

		public final String name;

		ElementType(String name) {
			this.name = name;
		}

		public String toString() {
			return name;
		}
	}

	/**
	 * Parent class for a basic OSM element node, way or relation
	 */
	public interface Element {

		ElementType getType();

		Map<String, String> getTags();

		/**
		 * @return the value associated with the given key
		 */
		String getValue(String key);
	}

	/**
	 * OSM node
	 */
	public interface Node extends Element, Identifiable<Node> {
		Coord getCoord();

		void setCoord(Coord coord);

		/**
		 * @return the ways of which this node is a member
		 */
		Map<Id<Way>, Way> getWays();

		/**
		 * @return the relations of which this node is a member
		 */
		Map<Id<Relation>, Relation> getRelations();
	}

	/**
	 * OSM way
	 */
	public interface Way extends Element, Identifiable<Way> {
		List<Node> getNodes();

		/**
		 * @return the relations of which this node is a member
		 */
		Map<Id<Relation>, Relation> getRelations();
	}

	/**
	 * OSM relation
	 */
	public interface Relation extends Element, Identifiable<Relation> {
		List<Element> getMembers();

		/**
		 * @return the role the given member has. <tt>null</tt> if element is not
		 * a member or no role is assigned
		 */
		String getMemberRole(Element member);

		/**
		 * @return the relations of which this node is a member
		 */
		Map<Id<Relation>, Relation> getRelations();
	}

	/**
	 * OSM tags used by the converters
	 */
	public static final class Key {

		public static final String NAME = "name";
		public static final String ROUTE = "route";
		public static final String ROUTE_MASTER = "route_master";

		public static final String PUBLIC_TRANSPORT = "public_transport";

		public static final String RAILWAY = "railway";
		public static final String HIGHWAY = "highway";
		public static final String SERVICE = "service";

		public final static String LANES = "lanes";
		public final static String MAXSPEED = "maxspeed";
		public final static String JUNCTION = "junction";
		public final static String ONEWAY = "oneway";
		public final static String ACCESS = "access";
		public static final String PSV = "psv";
		public static final String BUS = "bus";
		public static final String TAXI = "taxi";
		
		public final static String FORWARD = "forward";
		public final static String BACKWARD = "backward";

		// rarely used
		public static final String TYPE = "type";
		public static final String NETWORK = "network";
		public static final String VEHICLE = "vehicle";
		public static final String TUNNEL = "tunnel";
		public static final String TRAFFIC_CALMING = "traffic_calming";
		public static final String PASSING_PLACES = "passing_places";
		public static final String MOTORCYCLE = "motorcycle";
		public static final String FOOTWAY = "footway";
		public static final String CROSSING = "crossing";

		public static final List<String> DEFAULT_KEYS = Arrays.asList(
				NAME, ROUTE, ROUTE_MASTER, PUBLIC_TRANSPORT, RAILWAY, HIGHWAY, SERVICE, LANES, JUNCTION, ONEWAY, ACCESS, PSV,
				TYPE, NETWORK, VEHICLE, TUNNEL, TRAFFIC_CALMING, PASSING_PLACES, MOTORCYCLE, FOOTWAY, CROSSING);
		public static final List<String> DIRECTIONS = Arrays.asList(FORWARD, BACKWARD);
		
		public static String combinedKey(String... keyParts) {
			return Joiner.on(":").join(keyParts);
		}
	}
	
	/**
	 * OSM values used by the converters
	 */
	public static final class Value {

		public static final String STOP = "stop";
		public static final String STOP_FORWARD = "stop_forward";
		public static final String STOP_BACKWARD = "stop_backward";

		public static final String STOP_AREA = "stop_area";
		public static final String NODE = "node";
		public static final String BACKWARD = "backward";

		// values for highway=*
		public static final String MOTORWAY = "motorway";
		public static final String MOTORWAY_LINK = "motorway_link";
		public static final String TRUNK = "trunk";
		public static final String TRUNK_LINK = "trunk_link";
		public static final String PRIMARY = "primary";
		public static final String PRIMARY_LINK = "primary_link";
		public static final String SECONDARY = "secondary";
		public static final String SECONDARY_LINK = "secondary_link";
		public static final String TERTIARY = "tertiary";
		public static final String TERTIARY_LINK = "tertiary_link";
		public static final String UNCLASSIFIED = "unclassified";
		public static final String RESIDENTIAL = "residential";
		public static final String LIVING_STREET = "living_street";
		public static final String SERVICE = "service";
		public final static String STOP_POSITION = "stop_position";
		public final static String BUS = "bus";
		public final static String TROLLEYBUS = "trolleybus";

		public static final String FERRY = "ferry";

		// values for railway=*
		public static final String RAIL = "rail";
		public static final String TRAM = "tram";
		public static final String LIGHT_RAIL = "light_rail";
		public static final String FUNICULAR = "funicular";
		public static final String MONORAIL = "monorail";
		public static final String SUBWAY = "subway";

		// values for psv=*
		public static final String YES = "yes";
		public static final String DESIGNATED = "designated";
		
		// values for maxspeed=*
		public static final String WALK = "walk";
		public static final String NONE = "none";
	}


}
