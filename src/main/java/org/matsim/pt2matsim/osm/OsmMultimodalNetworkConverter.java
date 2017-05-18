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
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.AllowedTagsFilter;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.tools.NetworkTools;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts {@link OsmData} to a MATSim network, uses a config file ({@link OsmConverterConfigGroup})
 * to store conversion parameters and default values
 *
 * @author polettif
 */
public class OsmMultimodalNetworkConverter {

	private final static Logger log = Logger.getLogger(OsmMultimodalNetworkConverter.class);
	private final Map<String, OsmConverterConfigGroup.OsmWayParams> highwayParams = new HashMap<>();
	private final Map<String, OsmConverterConfigGroup.OsmWayParams> railwayParams = new HashMap<>();
	/**
	 * Maps for unknown entities
	 */
	private final Set<String> unknownHighways = new HashSet<>();
	private final Set<String> unknownRailways = new HashSet<>();
	private final Set<String> unknownWays = new HashSet<>();
	private final Set<String> unknownMaxspeedTags = new HashSet<>();
	private final Set<String> unknownLanesTags = new HashSet<>();
	/**
	 * connects osm way ids and link ids of the generated network
	 **/
	private final Map<Id<Link>, Id<Osm.Way>> osmIds = new HashMap<>();
	private OsmConverterConfigGroup config;
	private OsmData osmData;
	private Network network;
	private long id = 0;


	public OsmMultimodalNetworkConverter(OsmData osmData) {
		this.osmData = osmData;
	}

	/**
	 * Converts the osm data according to the parameters defined in config.
	 */
	public void convert(OsmConverterConfigGroup config) {
		this.config = config;
		CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("WGS84", config.getOutputCoordinateSystem());
		readWayParams();
		convertToNetwork(transformation);
		cleanRoadNetwork();
		if(config.getKeepTagsAsAttributes()) addAttributes();
	}

	/**
	 * reads the params from the config to different containers.
	 */
	private void readWayParams() {
		for(ConfigGroup e : config.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
			OsmConverterConfigGroup.OsmWayParams w = (OsmConverterConfigGroup.OsmWayParams) e;
			if(w.getOsmKey().equals(Osm.Key.HIGHWAY)) {
				highwayParams.put(w.getOsmValue(), w);
			} else if(w.getOsmKey().equals(Osm.Key.RAILWAY)) {
				railwayParams.put(w.getOsmValue(), w);
			}
		}
	}

	/**
	 * Converts the parsed osm data to MATSim nodes and links.
	 */
	private void convertToNetwork(CoordinateTransformation transformation) {

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

		// remove unusable ways
		log.info("remove unusable ways...");
		for(Osm.Way way : new HashSet<>(ways.values())) {
			if(!highwayParams.containsKey(way.getTags().get(Osm.Key.HIGHWAY)) && !railwayParams.containsKey(way.getTags().get(Osm.Key.RAILWAY)) && way.getRelations().size() == 0) {
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
				org.matsim.api.core.v01.network.Node nn = this.network.getFactory().createNode(Id.create(node.getId(), org.matsim.api.core.v01.network.Node.class), node.getCoord());
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
							createLink(this.network, way, fromNode, toNode, length);
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
	 * Creates a MATSim link from osm data
	 */
	private void createLink(final Network network, final Osm.Way way, final Osm.Node fromNode, final Osm.Node toNode, final double length) {
		double nofLanes;
		double laneCapacity;
		double freespeed;
		double freespeedFactor;
		boolean oneway;
		boolean onewayReverse = false;
		boolean busOnlyLink = false;

		// load defaults
		String highway = way.getTags().get(Osm.Key.HIGHWAY);
		String railway = way.getTags().get(Osm.Key.RAILWAY);
		OsmConverterConfigGroup.OsmWayParams wayValues;
		if(highway != null) {
			wayValues = this.highwayParams.get(highway);
			if(wayValues == null) {
				// check if bus route is on link
				if(way.getTags().containsKey(Osm.Key.PSV)) {
					busOnlyLink = true;
					wayValues = highwayParams.get(Osm.Value.UNCLASSIFIED);
				} else {
					this.unknownHighways.add(highway);
					return;
				}
			}
		} else if(railway != null) {
			wayValues = this.railwayParams.get(railway);
			if(wayValues == null) {
				this.unknownRailways.add(railway);
				return;
			}
		} else {
			this.unknownWays.add(way.getTags().values().toString());
			return;
		}
		nofLanes = wayValues.getLanes();
		laneCapacity = wayValues.getLaneCapacity();
		freespeed = wayValues.getFreespeed();
		freespeedFactor = wayValues.getFreespeedFactor();
		oneway = wayValues.getOneway();

		// check if there are tags that overwrite defaults
		// - check tag "junction"
		if("roundabout".equals(way.getTags().get(Osm.Key.JUNCTION))) {
			// if "junction" is not set in tags, get() returns null and equals() evaluates to false
			oneway = true;
		}
		// - check tag "oneway"
		String onewayTag = way.getTags().get(Osm.Key.ONEWAY);
		if(onewayTag != null) {
			if("yes".equals(onewayTag)) {
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
		// - check tag "oneway" with trunks, primary and secondary roads
		// 		(if they are marked as such, the default number of lanes should be two instead of one)
		if(highway != null) {
			if(highway.equalsIgnoreCase(Osm.Value.TRUNK) || highway.equalsIgnoreCase(Osm.Value.PRIMARY) || highway.equalsIgnoreCase(Osm.Value.SECONDARY)) {
				if(oneway && nofLanes == 1.0) {
					nofLanes = 2.0;
				}
			}
		}
		// - check tag "maxspeed"
		String maxspeedTag = way.getTags().get(Osm.Key.MAXSPEED);
		if(maxspeedTag != null) {
			try {
				freespeed = Double.parseDouble(maxspeedTag) / 3.6; // convert km/h to m/s
			} catch (NumberFormatException e) {
				boolean message = true;
				if(config.getGuessFreeSpeed()) {
					try {
						message = false;
						freespeed = Double.parseDouble(maxspeedTag.substring(0, 2)) / 3.6;
					} catch (NumberFormatException e1) {
						message = true;
					}
				}
				if(!this.unknownMaxspeedTags.contains(maxspeedTag) && message) {
					this.unknownMaxspeedTags.add(maxspeedTag);
					log.warn("Could not parse maxspeed tag: " + e.getMessage() + " (way " + way.getId() + ") Ignoring it.");
				}
			}
		}
		// - check tag "lanes"
		String lanesTag = way.getTags().get(Osm.Key.LANES);
		if(lanesTag != null) {
			try {
				double tmp = Double.parseDouble(lanesTag);
				if(tmp > 0) {
					nofLanes = tmp;
				}
			} catch (Exception e) {
				if(!this.unknownLanesTags.contains(lanesTag)) {
					this.unknownLanesTags.add(lanesTag);
					log.warn("Could not parse lanes tag: " + e.getMessage() + ". Ignoring it.");
				}
			}
		}

		// define the links' capacity and freespeed
		double capacity = nofLanes * laneCapacity;
		if(config.getScaleMaxSpeed()) {
			freespeed = freespeed * freespeedFactor;
		}

		// define modes allowed on link(s)
		//	basic type:
		Set<String> modes = new HashSet<>();
		if(!busOnlyLink && highway != null) {
			modes.add(TransportMode.car);
		}
		if(busOnlyLink) {
			modes.add("bus");
			modes.add(TransportMode.pt);
		}

		if(railway != null && railwayParams.containsKey(railway)) {
			modes.add(railway);
		}

		if(modes.isEmpty()) {
			modes.add("unknownStreetType");
		}

		//	public transport: get relation which this way is part of, then get the relations route=* (-> the mode)
		for(Osm.Relation rel : way.getRelations().values()) {
			String mode = rel.getTags().get(Osm.Key.ROUTE);
			if(mode != null) {
				if(mode.equals(Osm.Value.TROLLEYBUS)) {
					mode = Osm.Value.BUS;
				}
				modes.add(mode);
			}
		}


		// only create link, if both nodes were found, node could be null, since nodes outside a layer were dropped
		Id<org.matsim.api.core.v01.network.Node> fromId = Id.create(fromNode.getId(), org.matsim.api.core.v01.network.Node.class);
		Id<org.matsim.api.core.v01.network.Node> toId = Id.create(toNode.getId(), org.matsim.api.core.v01.network.Node.class);
		if(network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null) {
			if(!onewayReverse) {
				Link l = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(fromId), network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);

				network.addLink(l);
				osmIds.put(l.getId(), way.getId());
				this.id++;
			}
			if(!oneway) {
				Link l = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(toId), network.getNodes().get(fromId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);

				network.addLink(l);
				osmIds.put(l.getId(), way.getId());
				this.id++;
			}
		}
	}

	/**
	 * Adds attributes to the network link. Cannot be added directly upon link creation since we need to
	 * clean the road network and attributes are not be copied while filtering
	 */
	private void addAttributes() {
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
	private void cleanRoadNetwork() {
		Set<String> roadModes = CollectionUtils.stringToSet("car,bus");
		Network roadNetwork = NetworkTools.createFilteredNetworkByLinkMode(network, roadModes);
		Network restNetwork = NetworkTools.createFilteredNetworkExceptLinkMode(network, roadModes);
		this.network = null;

		new NetworkCleaner().run(roadNetwork);
		NetworkTools.integrateNetwork(restNetwork, roadNetwork);
		this.network = restNetwork;
	}


	/**
	 * @return the network
	 */
	public Network getNetwork() {
		return this.network;
	}

}

