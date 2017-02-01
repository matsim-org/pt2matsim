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


package org.matsim.pt2matsim.osm;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmImpl;
import org.matsim.pt2matsim.osm.parser.TagFilter;

import java.util.*;

/**
 * Creates an unmapped MATSim transit schedule from OSM. Converts
 * available public transit data from OSM to a schedule: stop facilities,
 * transitRoutes and routeProfiles. Departures and link sequences are missing.
 *
 * todo implement new framwork
 * @author polettif
 */
public class OsmTransitScheduleConverter {

	private static final Logger log = Logger.getLogger(OsmTransitScheduleConverter.class);

	private final CoordinateTransformation transformation;
	private final TransitSchedule transitSchedule;
	private final TransitScheduleFactory factory;
	private final String osmInput;
//	private OsmXmlParserHandler handler;

	// parser
	private Map<Long, OsmImpl.ParsedNode> nodes;
	private Map<Long, OsmImpl.ParsedRelation> relations;
	private Map<Long, OsmImpl.ParsedWay> ways;

	// filters
	private final TagFilter stop_area;
	private final TagFilter stop_position;
	private final TagFilter route_master;
	private final TagFilter ptRoute;

	private int routeNr = 0;

	public OsmTransitScheduleConverter(TransitSchedule schedule, CoordinateTransformation transformation, String osmInput) {
		this.transitSchedule = schedule;
		this.transformation = transformation;
		this.osmInput = osmInput;

		this.factory = transitSchedule.getFactory();

		// initialize filters
		stop_position = new TagFilter(Osm.ElementType.NODE);
		stop_position.add(Osm.Key.PUBLIC_TRANSPORT, Osm.Value.STOP_POSITION);

		stop_area = new TagFilter(Osm.ElementType.RELATION);
		stop_area.add(Osm.Key.PUBLIC_TRANSPORT, Osm.Value.STOP_AREA);

		route_master = new TagFilter(Osm.ElementType.RELATION);
		route_master.add(Osm.Key.ROUTE_MASTER, Osm.Value.BUS);
		route_master.add(Osm.Key.ROUTE_MASTER, Osm.Value.TROLLEYBUS);
		route_master.add(Osm.Key.ROUTE_MASTER, Osm.Value.TRAM);
		route_master.add(Osm.Key.ROUTE_MASTER, Osm.Value.MONORAIL);
		route_master.add(Osm.Key.ROUTE_MASTER, Osm.Value.SUBWAY);
		route_master.add(Osm.Key.ROUTE_MASTER, Osm.Value.FERRY);

		ptRoute = new TagFilter(Osm.ElementType.RELATION);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.BUS);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.TROLLEYBUS);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.RAIL);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.TRAM);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.LIGHT_RAIL);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.FUNICULAR);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.MONORAIL);
		ptRoute.add(Osm.Key.ROUTE, Osm.Value.SUBWAY);
	}

	/**
	 * Parses the osm file and converts it to a schedule
	 */
	public void run() {
		parse();
		convert();
	}

	private void parse() {
		TagFilter nodeFilter = new TagFilter(Osm.ElementType.NODE);
		nodeFilter.add(Osm.Key.PUBLIC_TRANSPORT, Osm.Value.STOP_POSITION);

		TagFilter wayFilter = new TagFilter(Osm.ElementType.WAY);

		TagFilter relationFilter = new TagFilter(Osm.ElementType.RELATION);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.BUS);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.TROLLEYBUS);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.RAIL);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.TRAM);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.LIGHT_RAIL);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.FUNICULAR);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.MONORAIL);
		relationFilter.add(Osm.Key.ROUTE, Osm.Value.SUBWAY);
		relationFilter.add(Osm.Key.ROUTE_MASTER, Osm.Value.BUS);
		relationFilter.add(Osm.Key.ROUTE_MASTER, Osm.Value.TROLLEYBUS);
		relationFilter.add(Osm.Key.ROUTE_MASTER, Osm.Value.TRAM);
		relationFilter.add(Osm.Key.ROUTE_MASTER, Osm.Value.MONORAIL);
		relationFilter.add(Osm.Key.ROUTE_MASTER, Osm.Value.SUBWAY);
		relationFilter.add(Osm.Key.ROUTE_MASTER, Osm.Value.FERRY);

//		handler = new OsmXmlParserHandler(nodeFilter, wayFilter, relationFilter);

//		OsmParser parser = new OsmParser();
//		parser.addHandler(handler);
//		parser.run(osmInput);
	}

	/**
	 * Converts relations, nodes and ways from osm to an
	 * unmapped MATSim Transit Schedule
	 */
	private void convert() {
		Map<Id<TransitLine>, TransitLine> transitLinesDump = new HashMap<>();

//		this.nodes = handler.getNodes();
//		this.relations = handler.getRelations();
//		this.ways = handler.getWays();

		/**
		 * Create TransitStopFacilities from public_transport=stop_position
		 */
		createStopFacilities();

		/**
		 * https://wiki.openstreetmap.org/wiki/Relation:route_master
		 */
		Set<Long> routesWithMaster = new HashSet<>();


		/**
		 * Create transitLines via route_masters
		 */
		for(OsmImpl.ParsedRelation relation : relations.values()) {
			if(route_master.matches(relation.tags)) {
				Id<TransitLine> lineId = createLineId(relation);
				TransitLine newTransitLine = factory.createTransitLine(lineId);
				newTransitLine.setName(relation.tags.get(Osm.Key.NAME));

				for(OsmImpl.ParsedRelationMember member : relation.members) {
					OsmImpl.ParsedRelation route = relations.get(member.refId);
					// maybe member route does not exist in area
					if(route != null) {
						TransitRoute newTransitRoute = createTransitRoute(route);
						if(newTransitRoute != null) {
							newTransitLine.addRoute(newTransitRoute);
							routesWithMaster.add(member.refId);
						}
					}
				}
				transitLinesDump.put(lineId, newTransitLine);
			}
		}

		/**
		 * Create transitRoutes without route_masters
		 */
		for(OsmImpl.ParsedRelation relation : relations.values()) {
			if(ptRoute.matches(relation.tags) && !routesWithMaster.contains(relation.id)) {
				Id<TransitLine> lineId = createLineId(relation);

				if(!transitLinesDump.containsKey(lineId)) {
					transitLinesDump.put(lineId, factory.createTransitLine(lineId));
				}

				TransitLine transitLine = transitLinesDump.get(lineId);

				TransitRoute newTransitRoute = createTransitRoute(relation);
				if(newTransitRoute != null) {
					transitLine.addRoute(newTransitRoute);
				}
			}
		}

		// add lines to schedule
		for(TransitLine transitLine : transitLinesDump.values()) {
				this.transitSchedule.addTransitLine(transitLine);
		}

		log.info("MATSim Transit Schedule created.");
	}

	/**
	 * creates stop facilities from nodes and adds them to the schedule
	 */
	private void createStopFacilities() {
		Map<Id<TransitStopFacility>, TransitStopFacility> stopFacilities = this.transitSchedule.getFacilities();

		// create facilities from stop_area first
		for(OsmImpl.ParsedRelation relation : relations.values()) {
			if(stop_area.matches(relation.tags)) {
				String stopPostAreaId = relation.tags.get(Osm.Key.NAME);

				// create a facility for each member
				for(OsmImpl.ParsedRelationMember member : relation.members) {
					if(member.role.equals(Osm.Value.STOP)) {
						TransitStopFacility newStopFacility = createStopFacilityFromOsmNode(nodes.get(member.refId), stopPostAreaId);

						if(!stopFacilities.containsValue(newStopFacility)) {
							this.transitSchedule.addStopFacility(newStopFacility);
						}
					}
				}
			}
		}

		// create other facilities
		for(OsmImpl.ParsedNode node : nodes.values()) {
			if(stop_position.matches(node.tags)) {
				if(!stopFacilities.containsKey(Id.create(node.id, TransitStopFacility.class))) {
					this.transitSchedule.addStopFacility(createStopFacilityFromOsmNode(node));
				}
			}
		}
	}

	/**
	 * creates a TransitStopFacility from an OsmNode
	 * @return the created facility
	 */
	private TransitStopFacility createStopFacilityFromOsmNode(OsmImpl.ParsedNode node, String stopPostAreaId) {
		Id<TransitStopFacility> id = Id.create(node.id, TransitStopFacility.class);
		Coord coord = transformation.transform(node.coord);
		TransitStopFacility newStopFacility = factory.createTransitStopFacility(id, coord, false);
		newStopFacility.setName(node.tags.get(Osm.Key.NAME));
		if(stopPostAreaId != null ) { newStopFacility.setStopPostAreaId(stopPostAreaId); }
		return newStopFacility;
	}

	private TransitStopFacility createStopFacilityFromOsmNode(OsmImpl.ParsedNode node) {
		return createStopFacilityFromOsmNode(node, null);
	}

	/**
	 * Creates a TransitRoute from a relation.
	 * @return <code>null</code> if the route has stops outside of the area
	 */
	private TransitRoute createTransitRoute(OsmImpl.ParsedRelation relation) {
		List<TransitRouteStop> stopSequenceForward = new ArrayList<>();

		// create different RouteStops and stopFacilities for forward and backward
		for(int i = 0; i < relation.members.size() - 1; i++) {
			OsmImpl.ParsedRelationMember member = relation.members.get(i);

			// route Stops
			if(member.type.equals(Osm.ElementType.NODE) && (Osm.Value.STOP.equals(member.role) || Osm.Value.STOP_FORWARD.equals(member.role))) {
				Id<TransitStopFacility> id = Id.create(member.refId, TransitStopFacility.class);
				TransitStopFacility transitStopFacility = transitSchedule.getFacilities().get(id);
				if(transitStopFacility == null) {
					return null;
				}
				// create transitRouteStop
				TransitRouteStop newRouteStop = factory.createTransitRouteStop(transitStopFacility, 0.0, 0.0);
				stopSequenceForward.add(newRouteStop);
			}

			// route links
//			if(member.type.equals(OsmParser.OsmRelationMemberType.WAY) && !OsmValue.BACKWARD.equals(member.role)) {
//				linkSequenceForward.add(Id.createLinkId(member.refId));
//			}
		}

//		NetworkRoute networkRoute = (linkSequenceForward.size() == 0 ? null : RouteUtils.createNetworkRoute(linkSequenceForward, null));
		if(stopSequenceForward.size() == 0){
			return null;
		}

		// one relation has two routes, forward and back
		Id<TransitRoute> transitRouteId = Id.create(createStringId(relation)+ (++routeNr), TransitRoute.class);
		TransitRoute newTransitRoute = factory.createTransitRoute(transitRouteId, null, stopSequenceForward, relation.tags.get(Osm.Key.ROUTE));
		newTransitRoute.addDeparture(factory.createDeparture(Id.create("departure" + routeNr, Departure.class), 60.0));

		return newTransitRoute;
	}

	private Id<TransitLine> createLineId(OsmImpl.ParsedRelation relation) {
		return Id.create(createStringId(relation), TransitLine.class);
	}

	private String createStringId(OsmImpl.ParsedRelation relation) {
		String id;
		boolean ref = false, operator=false, name=false;

		if(relation.tags.containsKey("name")) { name = true; }
		if(relation.tags.containsKey("ref")) { ref = true; }
		if(relation.tags.containsKey("operator")) { operator = true; }

		if(operator && ref) {
			id = relation.tags.get("operator")+": "+relation.tags.get("ref");
		}
		else if(operator && name) {
			id = relation.tags.get("operator")+": "+relation.tags.get("name");
		}
		else if(ref){
			id = relation.tags.get("ref");
		}
		else if(name) {
			id = relation.tags.get("name");
		}
		else {
			id = Long.toString(relation.id);
		}

		return id;
	}

	private Id<TransitLine> createLineId2(OsmImpl.ParsedRelation relation) {
		String id;
		boolean ref = false, operator=false, name=false;


		if(relation.tags.containsKey("ref")) { ref = true; }
		if(relation.tags.containsKey("operator")) { operator = true; }
		if(relation.tags.containsKey("name")) { name = true; }

		if(operator && ref) {
			id = relation.tags.get("operator")+"_"+relation.tags.get("ref");
		}
		else if(operator && name) {
			id = relation.tags.get("operator")+"_"+relation.tags.get("ref");
		}
		else if(name) {
			id = relation.tags.get("name");
		}
		else if(ref){
			id = relation.tags.get("ref");
		}
		else {
			id = Long.toString(relation.id);
		}

		try {
			return Id.create(id, TransitLine.class);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}