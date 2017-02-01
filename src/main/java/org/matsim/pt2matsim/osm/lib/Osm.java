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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author polettif
 */
public final class Osm {

	/**
	 * OSM tags used by the converters
	 */
	public enum Tag {
		NODE("node"),
		WAY("way"),
		RELATION("relation");

		public final String name;

		Tag(String name) {
			this.name = name;
		}

		public String toString() {
			return name;
		}
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
	}

	/**
	 * OSM values used by the converters
	 */
	public static final class OsmValue {

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
		public static final String TERTIARY = "tertiary";
		public static final String MINOR = "minor";
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
	}

	public static class Node {
		public final long id;
		public final Coord coord;
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public boolean used = false;
		public int ways = 0;

		public Node(final long id, final Coord coord) {
			this.id = id;
			this.coord = coord;
		}
	}

	public static class Way {
		public final long id;
		public final List<Long> nodes = new ArrayList<>(6);
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public boolean used = true;

		public Way(final long id) {
			this.id = id;
		}
	}

	public static class Relation {
		public final long id;
		public final List<RelationMember> members = new ArrayList<>(8);
		public final Map<String, String> tags = new HashMap<>(5, 0.9f);

		public Relation(final long id) {
			this.id = id;
		}
	}

	public static class RelationMember {
		public final Tag type;
		public final long refId;
		public final String role;

		public RelationMember(final Tag type, final long refId, final String role) {
			this.type = type;
			this.refId = refId;
			this.role = role;
		}
	}
}
