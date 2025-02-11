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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinks;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.LinkGeometryExporter.LinkDefinition;
import org.matsim.pt2matsim.osm.lib.AllowedTagsFilter;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.tools.NetworkTools;

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

	private static final Logger log = LogManager.getLogger(OsmMultimodalNetworkConverter.class);

	/**
	 * mode == null means "all modes"
	 */
	static record OsmTurnRestriction(Set<String> modes, List<Id<Osm.Way>> nextWayIds, RestrictionType restrictionType) {

		enum RestrictionType {
			PROHIBITIVE, // no_*
			MANDATORY; // only_*
		}

	}

	private static final Map<String, String> OSM_2_MATSIM_MODE_MAP = Map.of(
			Osm.Key.BUS, "bus",
			Osm.Key.BICYCLE, TransportMode.bike,
			Osm.Key.MOTORCYCLE, TransportMode.motorcycle,
			Osm.Key.MOTORCAR, TransportMode.car);

	private static final List<String> TURN_RESTRICTION_KEY_SUFFIXES = List.of(
			"", // for all modes
			":" + Osm.Key.BUS,
			":" + Osm.Key.BICYCLE,
			":" + Osm.Key.MOTORCAR);

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
	protected final Map<Id<Osm.Way>, List<Id<Link>>> wayLinkMap = new HashMap<>(); // reverse of osmIds
	protected final Map<Id<Link>, DisallowedNextLinks> disallowedNextLinks = new IdMap<>(Link.class);
	protected OsmConverterConfigGroup config;
	protected Network network;
	protected long id = 0;

	protected AllowedTagsFilter ptFilter;
	protected OsmConverterConfigGroup.OsmWayParams ptDefaultParams;
	protected LinkGeometryExporter geometryExporter;

	public OsmMultimodalNetworkConverter(OsmData osmData) {
		this.osmData = osmData;
	}

	/**
	 * Converts the OSM data according to the parameters defined in config.
	 */
	public void convert(OsmConverterConfigGroup config) {
		this.config = config;
		this.geometryExporter = new LinkGeometryExporter();
		CoordinateTransformation transformation = (config.getOutputCoordinateSystem() == null ?
				new IdentityTransformation() :
				TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, config.getOutputCoordinateSystem()));

		initPT();
		readWayParams();
		convertToNetwork(transformation);
		cleanNetwork();
		if (config.parseTurnRestrictions) {
			addDisallowedNextLinksAttributes();
		}
		if(config.getKeepTagsAsAttributes()) addAttributes();

		if (this.config.getOutputDetailedLinkGeometryFile() != null) {
			try {
				geometryExporter.onlyKeepGeometryForTheseLinks(network.getLinks().keySet());
				geometryExporter.writeToFile(Paths.get(this.config.getOutputDetailedLinkGeometryFile()));
			} catch (IOException e) {
				log.warn("Error while writing network geometry", e);
				e.printStackTrace();
			}
		}
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
				for(int i = 1; i < way.getNodes().size() - 1; i++) {
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

		// create reverse lookup map for link ids
		wayLinkMap.putAll(osmIds.entrySet().stream().collect(
				Collectors.groupingBy(Entry::getValue, Collectors.mapping(Entry::getKey, Collectors.toList()))));

		// parse turn restriction relations into disallowed links
		this.attachTurnRestrictionsAsDisallowedNextLinks();

		log.info("= conversion statistics: ==========================");
		log.info("MATSim: # nodes created: {}", this.network.getNodes().size());
		log.info("MATSim: # links created: {}", this.network.getLinks().size());

		if (!this.unknownHighways.isEmpty()) {
			log.info("The following highway-types had no defaults set and were thus NOT converted:");
			for(String highwayType : this.unknownHighways) {
				log.info("- \"{}\"", highwayType);
			}
		}
		if (!this.unknownRailways.isEmpty()) {
			log.info("The following railway-types had no defaults set and were thus NOT converted:");
			for(String railwayType : this.unknownRailways) {
				log.info("- \"{}\"", railwayType);
			}
		}
		if (!this.unknownWays.isEmpty()) {
			log.info("The way-types with the following tags had no defaults set and were thus NOT converted:");
			for(String wayType : this.unknownWays) {
				log.info("- \"{}\"", wayType);
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

		// TURN RESTRICTIONS
		List<OsmTurnRestriction> osmTurnRestrictions = this.parseTurnRestrictions(way, modes);

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
				Id<Link> linkId = Id.create(this.id, Link.class);
				Link l = network.getFactory().createLink(linkId, network.getNodes().get(fromId), network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freeSpeedForward);
				l.setCapacity(laneCountForward * laneCapacity);
				l.setNumberOfLanes(laneCountForward);
				l.setAllowedModes(modes);
				if (config.parseTurnRestrictions) {
					l.getAttributes().putAttribute(OsmTurnRestriction.class.getSimpleName(), osmTurnRestrictions);
				}

				network.addLink(l);
				osmIds.put(l.getId(), way.getId());
				geometryExporter.addLinkDefinition(linkId, new LinkDefinition(fromNode, toNode, way));
				this.id++;
			}
			// backward link
			if(!oneway) {
				Id<Link> linkId = Id.create(this.id, Link.class);
				Link l = network.getFactory().createLink(linkId, network.getNodes().get(toId), network.getNodes().get(fromId));
				l.setLength(length);
				l.setFreespeed(freeSpeedBackward);
				l.setCapacity(laneCountBackward * laneCapacity);
				l.setNumberOfLanes(laneCountBackward);
				l.setAllowedModes(modes);
				if (config.parseTurnRestrictions) {
					l.getAttributes().putAttribute(OsmTurnRestriction.class.getSimpleName(), osmTurnRestrictions);
				}

				network.addLink(l);
				osmIds.put(l.getId(), way.getId());
				geometryExporter.addLinkDefinition(linkId, new LinkDefinition(toNode, fromNode, way));
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
				log.warn("Could not parse '{}': {} (way {})", key, e.getMessage(), way.getId());
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
				log.warn("Could not parse '{}': {} (way {})", key, e.getMessage(), way.getId());
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
		ptFilter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TRAIN);
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
			Map<String, OsmConverterConfigGroup.OsmWayParams> highwayParams = this.wayParams.get(Osm.Key.HIGHWAY);
			if(highwayParams != null) {
				wayDefaults = highwayParams.get(highwayValue);
				if(wayDefaults == null) {
					unknownHighways.add(highwayValue);
				}
			}
		} else if(railwayValue != null) {
			Map<String, OsmConverterConfigGroup.OsmWayParams> railwayParams = this.wayParams.get(Osm.Key.RAILWAY);
			if(railwayParams != null) {
				wayDefaults = railwayParams.get(railwayValue);
				if(wayDefaults == null) {
					unknownRailways.add(railwayValue);
				}
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
	 * Adds DisallowedNextLinks attributes to links. See {@link #addAttributes()}
	 * documentation as to why this cannot be done directly when creating the link.
	 */
	private void addDisallowedNextLinksAttributes() {
		network.getLinks().values().forEach(link -> {
			DisallowedNextLinks dnl = disallowedNextLinks.get(link.getId());
			if (dnl != null) {
				NetworkUtils.setDisallowedNextLinks(link, dnl);
			}
		});
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
	 * Makes sure that consistent routable sub networks are created.
	 */
	protected void cleanNetwork() {
	    Set<String> subnetworkModes = new HashSet<>();
	    List<Network> subnetworks = new LinkedList<>();
	    
	    for (ConfigGroup params : config.getParameterSets(OsmConverterConfigGroup.RoutableSubnetworkParams.SET_NAME)) {
	        OsmConverterConfigGroup.RoutableSubnetworkParams subnetworkParams = (OsmConverterConfigGroup.RoutableSubnetworkParams) params;
	        subnetworkModes.add(subnetworkParams.getSubnetworkMode());
	        
	        log.info(String.format("Creating clean subnetwork for '%s' considering links of: %s", subnetworkParams.getSubnetworkMode(), subnetworkParams.getAllowedTransportModes().toString()));
	        
	        Network subnetwork = NetworkTools.createFilteredNetworkByLinkMode(network, subnetworkParams.getAllowedTransportModes());
	        new NetworkCleaner().run(subnetwork);
	        subnetwork.getLinks().values().forEach(l -> l.setAllowedModes(Collections.singleton(subnetworkParams.getSubnetworkMode())));
	        subnetworks.add(subnetwork);
	    }
	    
	    Set<String> remainingModes = new HashSet<>();
	    
	    for (Link link : network.getLinks().values()) {
	    	remainingModes.addAll(link.getAllowedModes());
	    }
	    
	    remainingModes.removeAll(subnetworkModes);
	    
	    log.info(String.format("Creating remaining network with modes: %s", remainingModes.toString()));
	    Network remainingNetwork = NetworkTools.createFilteredNetworkByLinkMode(network, remainingModes);
	    
	    for (Link link : remainingNetwork.getLinks().values()) {
	    	Set<String> newAllowedModes = new HashSet<>(remainingModes);
	    	newAllowedModes.retainAll(link.getAllowedModes());
	    	link.setAllowedModes(newAllowedModes);
	    }
	    
	    subnetworks.add(remainingNetwork);
	    
	    log.info("Creating combined network");
	    Network combinedNetwork = NetworkUtils.createNetwork();
	    subnetworks.forEach(n -> NetworkTools.integrateNetwork(combinedNetwork, n, true));
	    
	    this.network = combinedNetwork;
	}


	/**
	 * @return the network
	 */
	public Network getNetwork() {
		return this.network;
	}

	// Turn Restrictions

	@Nullable
	private List<OsmTurnRestriction> parseTurnRestrictions(final Osm.Way way, Set<String> modes) {

		if (!config.parseTurnRestrictions) {
			return null;
		}

		List<OsmTurnRestriction> osmTurnRestrictions = new ArrayList<>();
		for (Osm.Relation relation : way.getRelations().values()) {

			Map<String, String> relationTags = relation.getTags();

			// we only consider this relation, if
			// - it is a turn restriction relation and
			// - this way is the "from" link
			if (!(Osm.Key.RESTRICTION.equals(relationTags.get(Osm.Key.TYPE))
					&& Osm.Value.FROM.equals(relation.getMemberRole(way)))) {
				continue;
			}

			// identify modes
			Set<String> restrictionModes = new HashSet<>(modes);
			// remove except modes
			String exceptModesString = relationTags.get(Osm.Key.EXCEPT);
			if (exceptModesString != null) {
				for (String exceptMode : exceptModesString.split(";")) {
					String matsimExceptMode = OSM_2_MATSIM_MODE_MAP.getOrDefault(exceptMode, exceptMode);
					modes.remove(matsimExceptMode);
				}
			}

			// identify restriction type and eventually add modes
			OsmTurnRestriction.RestrictionType restrictionType = null;
			for (String suffix : TURN_RESTRICTION_KEY_SUFFIXES) {
				String restrictionTypeString = relationTags.get(Osm.Key.RESTRICTION + suffix);
				if (restrictionTypeString != null) {

					// add restriction type
					if (restrictionTypeString.startsWith(Osm.Key.PROHIBITORY_RESTRICTION_PREFIX)) {
						restrictionType = OsmTurnRestriction.RestrictionType.PROHIBITIVE;
					} else if (restrictionTypeString.startsWith(Osm.Key.MANDATORY_RESTRICTION_PREFIX)) {
						restrictionType = OsmTurnRestriction.RestrictionType.MANDATORY;
					}

					// add explicit modes, if
					// - suffix specified it and
					// - it is a MATSim mode
					if (suffix.length() > 1) {
						String mode = suffix.substring(1);
						String matsimMode = OSM_2_MATSIM_MODE_MAP.get(mode);
						if (matsimMode == null) {
							// skip this, if not one of MATSim modes
							restrictionType = null;
							continue;
						}
						restrictionModes.add(matsimMode);
					}

					break; // take first one
				}
			}
			if (restrictionType == null) {
				log.warn("Could not identify turn restriction relation: https://www.openstreetmap.org/relation/{}",
						relation.getId());
				continue;
			}

			// create intermediate turn restriction record
			List<Id<Osm.Way>> nextWayIds = new ArrayList<>();
			Id<Osm.Way> toWayId = null;
			for (Osm.Element element : relation.getMembers()) {
				if (element instanceof Osm.Way wayElement) {
					if (Osm.Value.TO.equals(relation.getMemberRole(wayElement))) {
						toWayId = wayElement.getId();
					} else if (Osm.Value.VIA.equals(relation.getMemberRole(wayElement))) {
						nextWayIds.add(wayElement.getId());
					}
				}
			}
			nextWayIds.add(toWayId);
			osmTurnRestrictions.add(new OsmTurnRestriction(restrictionModes, nextWayIds, restrictionType));
		}

		return osmTurnRestrictions;
	}

	private void attachTurnRestrictionsAsDisallowedNextLinks() {

		if (!config.parseTurnRestrictions) {
			return;
		}

		for (Link link : network.getLinks().values()) {

			// get turn restrictions
			List<OsmTurnRestriction> osmTurnRestrictions = (List<OsmTurnRestriction>) link.getAttributes()
					.getAttribute(OsmTurnRestriction.class.getSimpleName());
			if (osmTurnRestrictions == null) {
				break;
			}

			// create DisallowedNextLink
			for (OsmTurnRestriction tr : osmTurnRestrictions) {

				// find next link ids from next way ids
				List<Id<Link>> nextLinkIds = findLinkIds(wayLinkMap, network, link.getToNode(), tr.nextWayIds);
				if (nextLinkIds.size() == tr.nextWayIds.size()) { // found next link ids from this link's toNode

					// find link id lists to disallow
					List<List<Id<Link>>> disallowedNextLinkIdLists = new ArrayList<>();
					if (tr.restrictionType.equals(OsmTurnRestriction.RestrictionType.PROHIBITIVE)) {
						disallowedNextLinkIdLists.add(nextLinkIds);
					} else if (tr.restrictionType.equals(OsmTurnRestriction.RestrictionType.MANDATORY)) {
						// we need to exclude all other links originating from fromWay's toNode
						link.getToNode().getOutLinks().values().stream()
								.map(Link::getId)
								.filter(lId -> !lId.equals(nextLinkIds.get(0)))
								.forEach(lId -> disallowedNextLinkIdLists.add(List.of(lId)));
					}

					// attach DisallowedNextLinks objects
					DisallowedNextLinks dnl = new DisallowedNextLinks();
					for (List<Id<Link>> disallowedNextLinkIds : disallowedNextLinkIdLists) {
						for (String mode : tr.modes) {
							dnl.addDisallowedLinkSequence(mode, disallowedNextLinkIds);
						}
					}
					disallowedNextLinks.put(link.getId(), dnl);
				}

			}

			// remove attribute
			link.getAttributes().removeAttribute(OsmTurnRestriction.class.getSimpleName());
		}
	}

	// Statics

	/**
	 * Finds list of link ids starting from {@code lastNode} from list of OSM way
	 * ids.
	 * 
	 * @param wayLinkMap
	 * @param network
	 * @param lastNode
	 * @param wayIds
	 * @return
	 */
	protected static List<Id<Link>> findLinkIds(Map<Id<Osm.Way>, List<Id<Link>>> wayLinkMap, Network network,
			Node lastNode, List<Id<Osm.Way>> wayIds) {

		List<Id<Link>> linkIds = new ArrayList<>();

		int i = 0;
		do {
			Id<Osm.Way> wayId = wayIds.get(i);
			// for every link id, that could stem from this way
			List<Id<Link>> linkIdCandidates = wayLinkMap.get(wayId);
			if (linkIdCandidates == null) {
				// requested way id has no link ids -> turn restriction is incomplete
				return Collections.emptyList();
			}
			for (Id<Link> linkIdCandidate : linkIdCandidates) {
				if (lastNode.getId().equals(network.getLinks().get(linkIdCandidate).getFromNode().getId())) {
					linkIds.add(linkIdCandidate);
					i += 1;
					lastNode = network.getLinks().get(linkIds.get(linkIds.size() - 1)).getToNode();
					break;
				}
				// try next link candidate
			}
			if (i == 0) { // no linkCandidate was fitting -> lastNode is not attached to way ids
				return Collections.emptyList();
			}
		} while (i < wayIds.size());

		return linkIds;
	}

}

