/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.pt2matsim.osm;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.AllowedTagsFilter;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.tools.NetworkTools;

import java.util.*;

/**
 * Converts {@link OsmData} to a MATSim network, uses a config file
 * ({@link OsmConverterConfigGroup}) to store conversion parameters and default
 * values.
 * <p>
 * See OSM wiki for more documentation on the consumed data:
 * <dl>
 * <dt>lanes</dt>
 * <dd>https://wiki.openstreetmap.org/wiki/Key:lanes</dd>
 * <dt>freespeed / maxspeed</dt>
 * <dd>https://wiki.openstreetmap.org/wiki/Key:maxspeed</dd>
 * </dl>
 *
 * @author polettif
 * @author mstraub - Austrian Institute of Technology
 */
public class OsmMultimodalNetworkConverter {

	private final static Logger log = Logger.getLogger(OsmMultimodalNetworkConverter.class);
	
	static final int SPEED_LIMIT_WALK_KPH = 10;
	// // no speed limit (Germany) .. assume 200kph
	static final int SPEED_LIMIT_NONE_KPH = 200;

	protected final OsmData osmData;
	protected final Map<String, Map<String, OsmConverterConfigGroup.OsmWayParams>> wayParams = new HashMap<>();
	/**
	 * Maps for unknown entities
	 */
	protected final Set<String> unknownHighways = new HashSet<>();
	protected final Set<String> unknownRailways = new HashSet<>();
	protected final Set<String> unknownWays = new HashSet<>();
	protected final Set<String> unknownMaxspeedTags = new HashSet<>();
	protected final Set<String> unknownLanesTags = new HashSet<>();
	/**
	 * connects osm way ids and link ids of the generated network
	 **/
	protected final Map<Id<Link>, Id<Osm.Way>> osmIds = new HashMap<>();
	protected OsmConverterConfigGroup config;
	protected Network network;
	protected long id = 0;

	protected AllowedTagsFilter ptFilter;
	protected OsmConverterConfigGroup.OsmWayParams ptDefaultParams;

	public OsmMultimodalNetworkConverter(OsmData osmData) {
		this.osmData = osmData;
	}

	/**
	 * Converts the OSM data according to the parameters defined in config.
	 */
	public void convert(OsmConverterConfigGroup config) {
		this.config = config;
		CoordinateTransformation transformation = (config.getOutputCoordinateSystem() == null ?
				new IdentityTransformation() :
				TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, config.getOutputCoordinateSystem()));

		initPT();
		readWayParams();
		convertToNetwork(transformation);
		cleanNetwork();
		if(config.getKeepTagsAsAttributes()) addAttributes();
	}

	/**
	 * reads the params from the config to different containers.
	 */
	private void readWayParams() {
		for(ConfigGroup e : config.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
			OsmConverterConfigGroup.OsmWayParams w = (OsmConverterConfigGroup.OsmWayParams) e;
			wayParams.putIfAbsent(w.getOsmKey(), new HashMap<>());
			wayParams.get(w.getOsmKey()).put(w.getOsmValue(), w);
		}
	}

	/**
	 * Converts the parsed OSM data to MATSim nodes and links.
	 */
	protected void convertToNetwork(CoordinateTransformation transformation) {

		log.info("Converting OSM to MATSim network...");

		if(transformation == null) {
			transformation = TransformationFactory.getCoordinateTransformation("WGS84", "WGS84");
		}

		this.network = NetworkTools.createNetwork();

		Map<Id<Osm.Node>, Osm.Node> nodes = osmData.getNodes();
		Map<Id<Osm.Way>, Osm.Way> ways = osmData.getWays();
		Map<Id<Osm.Relation>, Osm.Relation> relations = osmData.getRelations();

		AllowedTagsFilter serviceRailTracksFilter = new AllowedTagsFilter();
		serviceRailTracksFilter.add(Osm.ElementType.WAY, Osm.Key.SERVICE, null);

		for(Osm.Node node : nodes.values()) {
			node.setCoord(transformation.transform(node.getCoord()));
		}

		// remove ways without default params
		log.info("remove unusable ways...");
		for(Osm.Way way : new HashSet<>(ways.values())) {
			if(getWayDefaultParams(way) == null) {
				osmData.removeWay(way.getId());
			}
		}

		// remove unused nodes
		log.info("remove nodes without ways...");
		for(Osm.Node n : new HashSet<>(nodes.values())) {
			if(n.getWays().size() == 0) {
				osmData.removeNode(n.getId());
			}
		}

		HashSet<Osm.Node> nodesToIgnore = new HashSet<>();

		log.info("cleaning network...");

		// Clean network:
		if(!config.getKeepPaths()) {
			// marked nodes as unused where only one way leads through
			// but only if this doesn't lead to links longer than MAX_LINKLENGTH
			for(Osm.Way way : ways.values()) {

				double length = 0.0;
				Osm.Node lastNode = way.getNodes().get(0);
				for(int i = 1; i < way.getNodes().size(); i++) {
					Osm.Node node = way.getNodes().get(i);
					if(node.getWays().size() > 1) {
						length = 0.0;
						lastNode = node;
					} else if(node.getWays().size() == 1) {
						length += CoordUtils.calcEuclideanDistance(lastNode.getCoord(), node.getCoord());
						if(length <= config.getMaxLinkLength()) {
							nodesToIgnore.add(node);
							lastNode = node;
						} else {
							length = 0.0;
							lastNode = node;
						}
					} else {
						log.warn("Way node with less than 1 way found.");
					}
				}
				// fix for some roundabouts with identical first and last node
				if(way.getNodes().get(0).equals(way.getNodes().get(way.getNodes().size() - 1))) {
					nodesToIgnore.remove(way.getNodes().get(0));
				}
			}
			// verify we did not mark nodes as unused that build a loop
			for(Osm.Way way : ways.values()) {
				int prevRealNodeIndex = 0;
				Osm.Node prevRealNode = way.getNodes().get(prevRealNodeIndex);

				for(int i = 1; i < way.getNodes().size(); i++) {
					Osm.Node node = way.getNodes().get(i);
					if(nodesToIgnore.contains(node)) {
						if(prevRealNode == node) {
							/* We detected a loop between two "real" nodes.
							 * Set some nodes between the start/end-loop-node to "used" again.
							 * But don't set all of them to "used", as we still want to do some network-thinning.
							 * I decided to use sqrt(.)-many nodes in between...
							 */
							double increment = Math.sqrt(i - prevRealNodeIndex);
							double nextNodeToKeep = prevRealNodeIndex + increment;
							for(double j = nextNodeToKeep; j < i; j += increment) {
								int index = (int) Math.floor(j);
								Osm.Node intermediaryNode = way.getNodes().get(index);
								nodesToIgnore.remove(intermediaryNode);
							}
						}
						prevRealNodeIndex = i;
						prevRealNode = node;
					}
				}
			}
		}

		// create the required nodes and add them to the network
		log.info("Creating nodes...");
		for(Osm.Node node : nodes.values()) {
			if(!nodesToIgnore.contains(node)) {
				Node nn = this.network.getFactory().createNode(Id.create(node.getId(), Node.class), node.getCoord());
				this.network.addNode(nn);
			}
		}

		// create the links
		log.info("Creating links...");
		this.id = 1;
		for(Osm.Way way : ways.values()) {
			Osm.Node fromNode = way.getNodes().get(0);
			double length = 0.0;
			Osm.Node lastToNode = fromNode;
			if(!nodesToIgnore.contains(fromNode)) {
				for(int i = 1, n = way.getNodes().size(); i < n; i++) {
					Osm.Node toNode = way.getNodes().get(i);
					if(toNode != lastToNode) {
						length += CoordUtils.calcEuclideanDistance(lastToNode.getCoord(), toNode.getCoord());
						if(!nodesToIgnore.contains(toNode)) {
							createLink(way, fromNode, toNode, length);
							fromNode = toNode;
							length = 0.0;
						}
						lastToNode = toNode;
					}
				}
			}
		}

		log.info("= conversion statistics: ==========================");
		log.info("MATSim: # nodes created: " + this.network.getNodes().size());
		log.info("MATSim: # links created: " + this.network.getLinks().size());

		if(this.unknownHighways.size() > 0) {
			log.info("The following highway-types had no defaults set and were thus NOT converted:");
			for(String highwayType : this.unknownHighways) {
				log.info("- \"" + highwayType + "\"");
			}
		}
		if(this.unknownRailways.size() > 0) {
			log.info("The following railway-types had no defaults set and were thus NOT converted:");
			for(String railwayType : this.unknownRailways) {
				log.info("- \"" + railwayType + "\"");
			}
		}
		if(this.unknownWays.size() > 0) {
			log.info("The way-types with the following tags had no defaults set and were thus NOT converted:");
			for(String wayType : this.unknownWays) {
				log.info("- \"" + wayType + "\"");
			}
		}
		log.info("= end of conversion statistics ====================");
	}

	/**
	 * Creates a MATSim link from OSM data
	 */
	protected void createLink(final Osm.Way way, final Osm.Node fromNode, final Osm.Node toNode, double length) {
		boolean oneway;
		boolean onewayReverse = false;
		double laneCapacity;
		Set<String> modes;

		// load defaults
		OsmConverterConfigGroup.OsmWayParams wayDefaultParams = getWayDefaultParams(way);
		laneCapacity = wayDefaultParams.getLaneCapacity();
		oneway = wayDefaultParams.getOneway();
		modes = new HashSet<>(wayDefaultParams.getAllowedTransportModes());

		// Overwrite defaults with OSM data
		Map<String, String> tags = way.getTags();
		String highwayValue = tags.get(Osm.Key.HIGHWAY);
		String railwayValue = tags.get(Osm.Key.RAILWAY);

		// ONEWAY
		if("roundabout".equals(way.getTags().get(Osm.Key.JUNCTION))) {
			// if "junction" is not set in tags, get() returns null and equals() evaluates to false
			oneway = true;
		}
		String onewayTag = way.getTags().get(Osm.Key.ONEWAY);
		if(onewayTag != null) {
			if(Osm.Value.YES.equals(onewayTag)) {
				oneway = true;
			} else if("true".equals(onewayTag)) {
				oneway = true;
			} else if("1".equals(onewayTag)) {
				oneway = true;
			} else if("-1".equals(onewayTag)) {
				onewayReverse = true;
				oneway = false;
			} else if("no".equals(onewayTag)) {
				oneway = false; // may be used to overwrite defaults
			}
		}
		
		// FREESPEED
		double freeSpeedDefault = wayDefaultParams.getFreespeed();
		double freeSpeedForward = calculateFreeSpeed(way, true, oneway || onewayReverse, freeSpeedDefault);
		double freeSpeedBackward = calculateFreeSpeed(way, false, oneway || onewayReverse, freeSpeedDefault);
		
		if(config.getScaleMaxSpeed()) {
			double freeSpeedFactor = wayDefaultParams.getFreespeedFactor();
			freeSpeedForward = freeSpeedForward * freeSpeedFactor;
			freeSpeedBackward = freeSpeedBackward * freeSpeedFactor;
		}

		// LANES
		double laneCountDefault = wayDefaultParams.getLanes();
		double laneCountForward = calculateLaneCount(way, true, oneway || onewayReverse, laneCountDefault);
		double laneCountBackward = calculateLaneCount(way, false, oneway || onewayReverse, laneCountDefault);

		// CAPACITY
		//double capacity = laneCountDefault * laneCapacity;

		// MODES
		// public transport: get relation which this way is part of, then get the relations route=* (-> the mode)
		for(Osm.Relation rel : way.getRelations().values()) {
			String mode = rel.getTags().get(Osm.Key.ROUTE);
			if(ptFilter.matches(rel) && mode != null) {
				if(mode.equals(Osm.Value.TROLLEYBUS)) {
					mode = Osm.Value.BUS;
				}
				modes.add(mode);
				modes.add(TransportMode.pt);
			}
		}
		
		// LENGTH
		if (length == 0.0) {
			log.warn("Attempting to create a link of length 0.0, which will mess up the routing. Fixing to 1.0!");
			length = 1.0;
		}

		// CREATE LINK
		// only create link, if both nodes were found, node could be null, since nodes outside a layer were dropped
		Id<Node> fromId = Id.create(fromNode.getId(), Node.class);
		Id<Node> toId = Id.create(toNode.getId(), Node.class);
		if(network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null) {
			// forward link (in OSM digitization direction)
			if(!onewayReverse) {
				Link l = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(fromId), network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freeSpeedForward);
				l.setCapacity(laneCountForward * laneCapacity);
				l.setNumberOfLanes(laneCountForward);
				l.setAllowedModes(modes);

				network.addLink(l);
				osmIds.put(l.getId(), way.getId());
				this.id++;
			}
			// backward link
			if(!oneway) {
				Link l = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(toId), network.getNodes().get(fromId));
				l.setLength(length);
				l.setFreespeed(freeSpeedBackward);
				l.setCapacity(laneCountBackward * laneCapacity);
				l.setNumberOfLanes(laneCountBackward);
				l.setAllowedModes(modes);

				network.addLink(l);
				osmIds.put(l.getId(), way.getId());
				this.id++;
			}
		}
	}
	
	private double calculateFreeSpeed(final Osm.Way way, boolean forward, boolean isOneway, double defaultFreeSpeed) {
		double maxspeed = parseMaxspeedValueAsMs(way, Osm.Key.MAXSPEED).orElse(defaultFreeSpeed);
		
		// in case a specific maxspeed per direction is available this overrules the standard maxspeed
		String direction = forward ? Osm.Key.FORWARD : Osm.Key.BACKWARD;
		Optional<Double> directedMaxspeed = parseMaxspeedValueAsMs(way, Osm.Key.combinedKey(Osm.Key.MAXSPEED, direction));
		if(directedMaxspeed.isPresent()) {
			maxspeed = directedMaxspeed.get();
		}
		
		return maxspeed;
	}
	
	/**
	 * @return speed in meters per second
	 */
	private Optional<Double> parseMaxspeedValueAsMs(final Osm.Way way, String key) {
		String value = way.getTags().get(key);
		if(value == null)
			return Optional.empty();
		
		// take first value if more values are given
		if(value.contains(";"))
			value = value.split(";")[0];
		
		double conversionDivisor = 3.6;
		if(value.contains("mph")) {
			conversionDivisor = 2.237;
			value = value.replaceAll("mph", "");
		} else if(value.contains("knots")) {
			conversionDivisor = 1.944;
			value = value.replaceAll("knots", "");
		}
		
		if(Osm.Value.NONE.equals(value)) {
			return Optional.of(SPEED_LIMIT_NONE_KPH / conversionDivisor);
		}
		else if(Osm.Value.WALK.equals(value)) {
			return Optional.of(SPEED_LIMIT_WALK_KPH / conversionDivisor);
		}
		
		try {
			return Optional.of(Double.parseDouble(value) / conversionDivisor);
		} catch (NumberFormatException e) {
			if(!unknownMaxspeedTags.contains(value)) {
				unknownMaxspeedTags.add(value);
				log.warn("Could not parse '" + key + "': " + e.getMessage() + " (way " + way.getId() + ")");
			}
			return Optional.empty();
		}
	}
	
	private double calculateLaneCount(final Osm.Way way, boolean forward, boolean isOneway, double defaultLaneCount) {
		double laneCount = parseLanesValue(way, Osm.Key.LANES).orElse(defaultLaneCount);

		// subtract lanes not accessible for cars
		List<String> blockingMots = Arrays.asList(Osm.Key.BUS, Osm.Key.PSV, Osm.Key.TAXI);
		for(String blockingMot : blockingMots)
			laneCount -= parseLanesValue(way, Osm.Key.combinedKey(Osm.Key.LANES, blockingMot)).orElse(0d);
		
		if(!isOneway)
			laneCount /= 2;
		
		// in case a specific lane count per direction is available this overrules the standard lanes
		String direction = forward ? Osm.Key.FORWARD : Osm.Key.BACKWARD;
		Optional<Double> directedLaneCount = parseLanesValue(way, Osm.Key.combinedKey(Osm.Key.LANES, direction));
		if(directedLaneCount.isPresent()) {
			laneCount = directedLaneCount.get();
			for(String blockingMot : blockingMots)
				laneCount -= parseLanesValue(way, Osm.Key.combinedKey(Osm.Key.LANES, blockingMot, direction)).orElse(0d);
		}

		// sanitize
		if(laneCount < 1)
			laneCount = 1;
		
		return laneCount;
	}
	
	private Optional<Double> parseLanesValue(final Osm.Way way, String key) {
		String value = way.getTags().get(key);
		if(value == null)
			return Optional.empty();
		
		// take first value if more values are given
		if(value.contains(";"))
			value = value.split(";")[0];
		
		try {
			return Optional.of(Double.parseDouble(value));
		} catch (NumberFormatException e) {
			if(!unknownLanesTags.contains(value)) {
				unknownLanesTags.add(value);
				log.warn("Could not parse '" + key + "': " + e.getMessage() + " (way " + way.getId() + ")");
			}
			return Optional.empty();
		}
	}

	private void initPT() {
		ptFilter = new AllowedTagsFilter();
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.BUS);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.TROLLEYBUS);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.TRAM);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.MONORAIL);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.SUBWAY);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.FERRY);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.BUS);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TROLLEYBUS);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.RAIL);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TRAM);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.LIGHT_RAIL);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.FUNICULAR);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.MONORAIL);
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.SUBWAY);
		ptFilter.add(Osm.ElementType.WAY, Osm.Key.PSV, Osm.Value.YES);
		ptFilter.add(Osm.ElementType.WAY, Osm.Key.PSV, Osm.Value.DESIGNATED);
		ptFilter.add(Osm.ElementType.WAY, Osm.Key.BUS, Osm.Value.DESIGNATED);
		ptFilter.add(Osm.ElementType.WAY, Osm.Key.BUS, Osm.Value.DESIGNATED);

		ptDefaultParams = new OsmConverterConfigGroup.OsmWayParams("NULL", "NULL",
				1, 50 / 3.6, 1.0, 9999,
				false, Collections.singleton(TransportMode.pt));
	}

	protected boolean wayHasPublicTransit(Osm.Way way) {
		if(ptFilter.matches(way)) {
			return true;
		}
		for(Osm.Relation relation : way.getRelations().values()) {
			if(ptFilter.matches(relation)) {
				return true;
			}
		}
		return false;
	}

	protected OsmConverterConfigGroup.OsmWayParams getWayDefaultParams(Osm.Way way) {
		Map<String, String> tags = way.getTags();
		String highwayValue = tags.get(Osm.Key.HIGHWAY);
		String railwayValue = tags.get(Osm.Key.RAILWAY);

		OsmConverterConfigGroup.OsmWayParams wayDefaults = null;
		if(highwayValue != null) {
			wayDefaults = this.wayParams.get(Osm.Key.HIGHWAY).get(highwayValue);
			if(wayDefaults == null) {
				unknownHighways.add(highwayValue);
			}
		} else if(railwayValue != null) {
			wayDefaults = this.wayParams.get(Osm.Key.RAILWAY).get(railwayValue);
			if(wayDefaults == null) {
				unknownRailways.add(railwayValue);
			}
		} else {
			unknownWays.add(way.getTags().values().toString());
		}

		if(wayDefaults == null) {
			if(wayHasPublicTransit(way) && config.keepHighwaysWithPT()) {
				wayDefaults = ptDefaultParams;
			}
		}

		return wayDefaults;
	}

	/**
	 * Adds attributes to the network link. Cannot be added directly upon link creation since we need to
	 * clean the road network and attributes are not copied while filtering
	 */
	protected void addAttributes() {
		for(Link link : this.network.getLinks().values()) {
			Osm.Way way = osmData.getWays().get(osmIds.get(link.getId()));

			// way id
			link.getAttributes().putAttribute(OsmConverterConfigGroup.LINK_ATTRIBUTE_WAY_ID, Long.parseLong(way.getId().toString()));

			// default tags
			for(Map.Entry<String, String> t : way.getTags().entrySet()) {
				if(Osm.Key.DEFAULT_KEYS.contains(t.getKey())) {
					String key = OsmConverterConfigGroup.LINK_ATTRIBUTE_WAY_PREFIX + t.getKey();
					String val = t.getValue();
					link.getAttributes().putAttribute(key.replace("&", "AND"), val.replace("&", "AND"));
				}
			}

			// relation info
			for(Osm.Relation rel : way.getRelations().values()) {
				// route
				String route = rel.getTags().get(Osm.Key.ROUTE);
				if(route != null) {
					String osmRouteKey = OsmConverterConfigGroup.LINK_ATTRIBUTE_RELATION_ROUTE;
					Set<String> attr = new HashSet<>(CollectionUtils.stringToSet((String) link.getAttributes().getAttribute(osmRouteKey)));
					attr.add(route);
					link.getAttributes().putAttribute(osmRouteKey, CollectionUtils.setToString(attr));
				}

				// route master
				String route_master = rel.getTags().get(Osm.Key.ROUTE_MASTER);
				if(route_master != null) {
					String osmRouteMasterKey = OsmConverterConfigGroup.LINK_ATTRIBUTE_RELATION_ROUTE_MASTER;
					Set<String> attr = new HashSet<>(CollectionUtils.stringToSet((String) link.getAttributes().getAttribute(osmRouteMasterKey)));
					attr.add(route_master);
					link.getAttributes().putAttribute(osmRouteMasterKey, CollectionUtils.setToString(attr));
				}
			}
		}
	}

	/**
	 * Runs the network cleaner on the street network.
	 */
	protected void cleanNetwork() {
		Network carNetwork = NetworkTools.createFilteredNetworkByLinkMode(network, Collections.singleton("car"));
		new NetworkCleaner().run(carNetwork);
		
		Set<String> busModes = new HashSet<>(Arrays.asList("car", "bus"));
		Network busNetwork = NetworkTools.createFilteredNetworkByLinkMode(network, busModes);
		new NetworkCleaner().run(busNetwork);
		busNetwork.getLinks().values().forEach(l -> l.setAllowedModes(Collections.singleton("bus")));
		
		Network restNetwork = NetworkTools.createFilteredNetworkExceptLinkMode(network, busModes);
		
		Network combinedNetwork = NetworkUtils.createNetwork();
		NetworkTools.integrateNetwork(combinedNetwork, restNetwork, true);
		NetworkTools.integrateNetwork(combinedNetwork, busNetwork, true);
		NetworkTools.integrateNetwork(combinedNetwork, carNetwork, true);
		
		this.network = combinedNetwork;
	}


	/**
	 * @return the network
	 */
	public Network getNetwork() {
		return this.network;
	}

}

