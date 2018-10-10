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

package org.matsim.pt2matsim.tools;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkTransform;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.filter.NetworkLinkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersFactory;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersStandard;

import java.util.*;

import static org.matsim.pt2matsim.tools.ScheduleTools.getTransitRouteLinkIds;

/**
 * Provides Tools for analysing and manipulating networks.
 *
 * @author polettif
 */
public final class NetworkTools {

	protected static Logger log = Logger.getLogger(NetworkTools.class);

	private NetworkTools() {}

	/**
	 * Reads and returns a network
	 */
	public static Network readNetwork(String fileName) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(fileName);
		return network;
	}

	public static void writeNetwork(Network network, String fileName) {
		new NetworkWriter(network).write(fileName);
	}

	public static Network createNetwork() {
		return NetworkUtils.createNetwork();
	}

	public static void transformNetwork(Network network, String fromCoordinateSystem, String toCoordinateSystem) {
		new NetworkTransform(TransformationFactory.getCoordinateTransformation(fromCoordinateSystem, toCoordinateSystem)).run(network);
	}

	public static void transformNetworkFile(String networkFile, String fromCoordinateSystem, String toCoordinateSystem) {
		log.info("... Transformig network from " + fromCoordinateSystem + " to " + toCoordinateSystem);
		Network network = readNetwork(networkFile);
		transformNetwork(network, fromCoordinateSystem, toCoordinateSystem);
		writeNetwork(network, networkFile);
	}

	/**
	 * Returns the nearest link for the given coordinate.
	 * Looks for nodes within search radius of coord (using {@link NetworkUtils#getNearestNodes},
	 * fetches all in- and outlinks returns the link with the smallest distance
	 * to the given coordinate. If there are two opposite links, the link with
	 * the coordinate on its right side is returned.<p/>
	 *
	 * NOTE: In contrast to {@link NetworkUtils#getNearestLink}, this method looks for the
	 * nearest link passing the coordinate from a reasonable set of nearby nodes instead
	 * of the closest link originating from or ending in the closest node.
	 *
	 * @param network network
	 * @param coord   the coordinate
	 * @param nodeSearchRadius links from/to nodes within this radius are considered
	 */
	public static Link getNearestLink(Network network, Coord coord, double nodeSearchRadius) {
		Link closestLink = null;
		double minDistance = Double.MAX_VALUE;

		Collection<Node> nearestNodes = NetworkUtils.getNearestNodes(network, coord, nodeSearchRadius);

		while(nearestNodes.size() == 0) {
			nodeSearchRadius *= 2;
			nearestNodes = NetworkUtils.getNearestNodes(network, coord, nodeSearchRadius);
		}
		// check every in- and outlink of each node
		for(Node node : nearestNodes) {
			Set<Link> links = new HashSet<>(node.getOutLinks().values());
			links.addAll(node.getInLinks().values());
			double lineSegmentDistance;

			for(Link link : links) {
				// only use links with a viable network transport mode
				lineSegmentDistance = CoordUtils.distancePointLinesegment(link.getFromNode().getCoord(), link.getToNode().getCoord(), coord);

				if(lineSegmentDistance < minDistance) {
					minDistance = lineSegmentDistance;
					closestLink = link;
				}

			}
		}

		// check for opposite link
		Link oppositeLink = getOppositeLink(closestLink);
		if(oppositeLink != null && !coordIsOnRightSideOfLink(coord, closestLink)) {
			return oppositeLink;
		} else {
			return closestLink;
		}
	}


	/**
	 * Looks for nodes within search radius of <tt>coord</tt> (using {@link NetworkUtils#getNearestNodes},
	 * fetches all in- and outlinks and sorts them ascending by their
	 * distance to the coordinates given. A map with the distance as key and a set as value is used
	 * to (1) return the already calculated distance to the coord and (2) store two opposite links under
	 * the same distance.
	 *
	 * @param network               The network
	 * @param coord                 the coordinate from which the closest links are
	 *                              to be searched
	 * @param nodeSearchRadius      Only links from and to nodes within this radius are considered.
	 * @param allowedTransportModes Only links with at least one of these transport modes are considered. All links are considered if <tt>null</tt>.
	 */
	 public static Map<Double, Set<Link>> findClosestLinks(Network network, Coord coord, double nodeSearchRadius, Set<String> allowedTransportModes) {
		 Collection<Node> nearestNodes = NetworkUtils.getNearestNodes(network, coord, nodeSearchRadius);
		 SortedMap<Double, Set<Link>> closestLinksSortedByDistance = new TreeMap<>();

		 if(nearestNodes.size() != 0) {
			 // fetch every in- and outlink of each node
			 HashSet<Link> links = new HashSet<>();
			 for(Node node : nearestNodes) {
				 links.addAll(node.getOutLinks().values());
				 links.addAll(node.getInLinks().values());
			 }

			 // calculate lineSegmentDistance for all links
			 for(Link link : links) {
				 // only use links with a viable network transport mode
				 if(allowedTransportModes == null || MiscUtils.collectionsShareMinOneStringEntry(link.getAllowedModes(), allowedTransportModes)) {
					 double lineSegmentDistance = CoordUtils.distancePointLinesegment(link.getFromNode().getCoord(), link.getToNode().getCoord(), coord);
					 MapUtils.getSet(lineSegmentDistance, closestLinksSortedByDistance).add(link);
				 }
			 }
		 }
		 return closestLinksSortedByDistance;
	}

	/**
	 * See {@link #findClosestLinks}. Returns a list ordered ascending by distance to the coord.
	 * For opposite links, the link which has the coordinate on its right side is sorted "closer" to the coordinate.
	 * If more than two links have the exact same distance, links are sorted by distance to their respective closest node.
	 * After that, behaviour is undefined.
	 */
	public static List<Link> findClosestLinksSorted(Network network, Coord coord, double nodeSearchRadius, Set<String> allowedTransportModes) {
		List<Link> links = new ArrayList<>();
		Map<Double, Set<Link>> sortedLinks = findClosestLinks(network, coord, nodeSearchRadius, allowedTransportModes);

		for(Set<Link> set : sortedLinks.values()) {
			List<Link> list = new ArrayList<>(set);
			if(list.size() == 1) {
				links.add(list.get(0));
			} else if(list.size() == 2) {
				if(coordIsOnRightSideOfLink(coord, list.get(0))) {
					links.add(list.get(0));
					links.add(list.get(1));
				} else {
					links.add(list.get(1));
					links.add(list.get(0));
				}
			} else {
				Map<Double, Link> tmp = new HashMap<>();
				for(Link l : list) {
					double fromNodeDist = CoordUtils.calcEuclideanDistance(l.getFromNode().getCoord(), coord);
					double toNodeDist = CoordUtils.calcEuclideanDistance(l.getFromNode().getCoord(), coord);
					double nodeDist = fromNodeDist < toNodeDist ? fromNodeDist : toNodeDist;

					double d = nodeDist + (coordIsOnRightSideOfLink(coord, l) ? 1 : 100);
					while(tmp.putIfAbsent(d, l) == null) {
						d += 0.01;
					}
				}
				links.addAll(tmp.values());
			}
		}
		return links;
	}

	/**
	 * Creates and returns a mode filtered network.
	 *
	 * @param network        the input network, is not modified
	 * @param transportModes Links of the input network that share at least one network mode
	 *                       with this set are added to the new network. The returned network
	 *                       is empty if <tt>null</tt>.
	 * @return the filtered new network
	 */
	public static Network createFilteredNetworkByLinkMode(Network network, Set<String> transportModes) {
		NetworkFilterManager filterManager = new NetworkFilterManager(network);
		filterManager.addLinkFilter(new LinkFilter(transportModes));
		Network newNetwork = filterManager.applyFilters();
		removeNotUsedNodes(newNetwork);
		return newNetwork;
	}

	public static Network createFilteredNetworkExceptLinkMode(Network network, Set<String> transportModes) {
		NetworkFilterManager filterManager = new NetworkFilterManager(network);
		filterManager.addLinkFilter(new InverseLinkFilter(transportModes));
		return filterManager.applyFilters();
	}

	public static void cutNetwork(Network network, Coord SW, Coord NE) {
		for(Node n : new HashSet<>(network.getNodes().values())) {
			if(!CoordTools.isInArea(n.getCoord(), SW, NE)) {
				network.removeNode(n.getId());
			}
		}
	}

	/**
	 * @return the opposite direction link. <tt>null</tt> if there is no opposite link.
	 */
	public static Link getOppositeLink(Link link) {
		if(link == null) {
			return null;
		}

		Link oppositeDirectionLink = null;
		Map<Id<Link>, ? extends Link> inLinks = link.getFromNode().getInLinks();
		if(inLinks != null) {
			for(Link inLink : inLinks.values()) {
				if(inLink.getFromNode().equals(link.getToNode())) {
					oppositeDirectionLink = inLink;
				}
			}
		}

		return oppositeDirectionLink;
	}

	/**
	 * @return true if the coordinate is on the right hand side of the link (or on the link).
	 */
	public static boolean coordIsOnRightSideOfLink(Coord coord, Link link) {
		return CoordTools.coordIsOnRightSideOfLine(coord, link.getFromNode().getCoord(), link.getToNode().getCoord());
	}

	/**
	 * Checks if a link sequence has loops (i.e. the same link is passed twice).
	 */
	public static boolean linkSequenceHasDuplicateLink(List<Link> linkSequence) {
		Set tmpSet = new HashSet<>(linkSequence);
		return tmpSet.size() < linkSequence.size();
	}


	/**
	 * Checks if a link sequence has u-turns (i.e. the opposite direction link is
	 * passed immediately after a link).
	 */
	public static boolean linkSequenceHasUTurns(List<Link> links) {
		for(int i = 1; i < links.size(); i++) {
			if(links.get(i).getToNode().equals(links.get(i - 1).getFromNode())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A debug method to assign weights to network links as number of lanes.
	 * The network is changed permanently, so this should really only be used for
	 * debugging.
	 */
	public static void visualizeWeightsAsLanes(Network network, Map<Id<Link>, Double> weightMap) {
		for(Map.Entry<Id<Link>, Double> w : weightMap.entrySet()) {
			network.getLinks().get(w.getKey()).setNumberOfLanes(w.getValue());
		}
	}

	/**
	 * @return the network links from a given list of link ids
	 */
	public static List<Link> getLinksFromIds(Network network, List<Id<Link>> linkIds) {
		Map<Id<Link>, ? extends Link> links = network.getLinks();
		List<Link> list = new ArrayList<>();
		for(Id<Link> linkId : linkIds) {
			list.add(links.get(linkId));
		}
		return list;
	}

	/**
	 * Merges all network into baseNetworks. If a link id already
	 * exists in the base network, the link is not added to it.
	 *
	 * @param baseNetwork the network in which all other networks are integrated
	 * @param networks    collection of networks to merge into the base network
	 */
	public static void mergeNetworks(Network baseNetwork, Collection<Network> networks) {
		log.info("Merging networks...");

		int numberOfLinksBefore = baseNetwork.getLinks().size();
		int numberOfNodesBefore = baseNetwork.getNodes().size();

		for(Network currentNetwork : networks) {
			integrateNetwork(baseNetwork, currentNetwork, true);
		}

		log.info("... Total number of links added to network: " + (baseNetwork.getLinks().size() - numberOfLinksBefore));
		log.info("... Total number of nodes added to network: " + (baseNetwork.getNodes().size() - numberOfNodesBefore));
		log.info("Merging networks... done.");
	}

	/**
	 * Integrates <tt>network B</tt> into <tt>network A</tt>. Network
	 * A contains all links and nodes of both networks
	 * after integration.
	 */
	public static void integrateNetwork(final Network networkA, final Network networkB, boolean mergeModes) {
		final NetworkFactory factory = networkA.getFactory();

		// Nodes
		for(Node node : networkB.getNodes().values()) {
			Id<Node> nodeId = Id.create(node.getId().toString(), Node.class);
			if(!networkA.getNodes().containsKey(nodeId)) {
				Node newNode = factory.createNode(nodeId, node.getCoord());
				networkA.addNode(newNode);
			}
		}

		// Links
		double capacityFactor = networkA.getCapacityPeriod() / networkB.getCapacityPeriod();
		for(Link link : networkB.getLinks().values()) {
			Id<Link> linkId = Id.create(link.getId().toString(), Link.class);
			if(!networkA.getLinks().containsKey(linkId)) {
				Id<Node> fromNodeId = Id.create(link.getFromNode().getId().toString(), Node.class);
				Id<Node> toNodeId = Id.create(link.getToNode().getId().toString(), Node.class);
				Link newLink = factory.createLink(linkId, networkA.getNodes().get(fromNodeId), networkA.getNodes().get(toNodeId));
				newLink.setAllowedModes(link.getAllowedModes());
				newLink.setCapacity(link.getCapacity() * capacityFactor);
				newLink.setFreespeed(link.getFreespeed());
				newLink.setLength(link.getLength());
				newLink.setNumberOfLanes(link.getNumberOfLanes());
				networkA.addLink(newLink);
			} else if (mergeModes) {
				Link existingLink = networkA.getLinks().get(linkId);
				
				Set<String> allowedModes = new HashSet<>();
				allowedModes.addAll(existingLink.getAllowedModes());
				allowedModes.addAll(link.getAllowedModes());
				
				existingLink.setAllowedModes(allowedModes);
				
				if (link.getCapacity() * capacityFactor != existingLink.getCapacity()) {
					throw new IllegalStateException("Capacity must be equal for integration");
				}
				
				if (link.getFreespeed() != existingLink.getFreespeed()) {
					throw new IllegalStateException("Freespeed must be equal for integration");
				}
				
				if (link.getLength() != existingLink.getLength()) {
					throw new IllegalStateException("Length must be equal for integration");
				}
				
				if (link.getNumberOfLanes() != existingLink.getNumberOfLanes()) {
					throw new IllegalStateException("Number of lanes must be equal for integration");
				}
			}
		}
	}

	public static void shortenLink(Link link, Node toNode) {
		link.setToNode(toNode);
		link.setLength(CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), toNode.getCoord()));
	}

	public static void shortenLink(Node fromNode, Link link) {
		link.setFromNode(fromNode);
		link.setLength(CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), fromNode.getCoord()));
	}


	/**
	 * Sets the free speed of all links with the networkMode to the
	 * defined value.
	 */
	public static void setFreeSpeedOfLinks(Network network, String networkMode, double freespeedValue) {
		for(Link link : network.getLinks().values()) {
			if(link.getAllowedModes().contains(networkMode)) {
				link.setFreespeed(freespeedValue);
			}
		}
	}

	/**
	 * Resets the link length of all links with the given link Mode
	 */
	public static void resetLinkLength(Network network, String networkMode) {
		for(Link link : network.getLinks().values()) {
			if(link.getAllowedModes().contains(networkMode)) {
				double l = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());
				link.setLength(l > 0 ? l : 1);
			}
		}
	}

	/**
	 * Creates mode dependent routers based on the actual network modes used.
	 */
	public static ScheduleRoutersFactory guessRouters(TransitSchedule schedule, Network network) {
		// for each schedule modes, look which network modes are used
		Map<String, Set<String>> modeAssignments = new HashMap<>();
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Set<String> usedNetworkModes = MapUtils.getSet(transitRoute.getTransportMode(), modeAssignments);
				List<Link> links = getLinksFromIds(network, getTransitRouteLinkIds(transitRoute));
				for(Link link : links) {
					usedNetworkModes.addAll(link.getAllowedModes());
				}
			}
		}

		// setup config
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		for(Map.Entry<String, Set<String>> entry : modeAssignments.entrySet()) {
			PublicTransitMappingConfigGroup.TransportModeAssignment mra = new PublicTransitMappingConfigGroup.TransportModeAssignment(entry.getKey());
			mra.setNetworkModes(entry.getValue());
			config.addParameterSet(mra);
		}

		return new ScheduleRoutersStandard.Factory(schedule, network, modeAssignments, PublicTransitMappingConfigGroup.TravelCostType.linkLength, true);
	}

	/**
	 * Replaces all non-car link modes with "pt"
	 */
	public static void replaceNonCarModesWithPT(Network network) {
		log.info("... Replacing all non-car link modes with \"pt\"");

		Set<String> modesCar = Collections.singleton(TransportMode.car);

		Set<String> modesCarPt = new HashSet<>();
		modesCarPt.add(TransportMode.car);
		modesCarPt.add(TransportMode.pt);

		Set<String> modesPt = new HashSet<>();
		modesPt.add(TransportMode.pt);

		for(Link link : network.getLinks().values()) {
			if(link.getAllowedModes().size() == 0 && link.getAllowedModes().contains(TransportMode.car)) {
				link.setAllowedModes(modesCar);
			}
			if(link.getAllowedModes().size() > 0 && link.getAllowedModes().contains(TransportMode.car)) {
				link.setAllowedModes(modesCarPt);
			} else if(!link.getAllowedModes().contains(TransportMode.car)) {
				link.setAllowedModes(modesPt);
			}
		}
	}

	/**
	 * @return only links that have the same allowed modes set
	 */
	public static Set<Link> filterLinkSetExactlyByModes(Collection<? extends Link> links, Set<String> transportModes) {
		Set<Link> returnSet = new HashSet<>();
		for(Link l : links) {
			if(l.getAllowedModes().equals(transportModes)) {
				returnSet.add(l);
			}
		}
		return returnSet;
	}

	/**
	 * Removes all nodes with no in- or outgoing links from a network
	 */
	public static void removeNotUsedNodes(Network network) {
		for(Node n : new HashSet<>(network.getNodes().values())) {
			if(n.getInLinks().size() == 0 && n.getOutLinks().size() == 0) {
				network.removeNode(n.getId());
			}
		}
	}

	/**
	 * Removes all nodes that are not specified from the network
	 */
	public static void cutNetwork(Network network, Collection<Node> nodesToKeep) {
		for(Node n : new HashSet<>(network.getNodes().values())) {
			if(!nodesToKeep.contains(n)) {
				network.removeNode(n.getId());
			}
		}
	}

	/**
	 * Links that only have one preceding and succeeding link (ignoring opposite links)
	 * are removed except the link in that sequence that is closest to the coordinate
	 */
	public static void reduceSequencedLinks(Collection<? extends Link> links, Coord coord) {
		Map<Link, Link> linkSequence = new HashMap<>(); // key points to succeeding link
		Set<Link> originLinks = new HashSet<>();		// links from which a sequence of single file links originates
		Map<Link, Link> linksToKeep = new HashMap<>();	// links from single file sequence that are closest to the coord

		// get all single file links
		Set<Link> singleFileLinks = new HashSet<>();
		for(Link l : links) {
			if(getSingleFilePrecedingLink(l) != null || getSingleFileSucceedingLink(l) != null) {
				singleFileLinks.add(l);
			}
		}
		
		// find origin links
		Set<Link> found = new HashSet<>();	
		Set<Link> visited = new HashSet<>();
		
		for(Link currentLink : singleFileLinks) {
			if(!found.contains(currentLink)) {
				Link actual = currentLink;
				Link precedingLink;
				visited.clear();
				do {
					found.add(actual);
					visited.add(actual);
					precedingLink = getSingleFilePrecedingLink(actual);
					if(precedingLink != null && links.contains(precedingLink)) {
						linkSequence.put(precedingLink, actual);
						actual = precedingLink;
						
						if (visited.contains(actual)) {
							// We found a closed loop and arrived back at the starting point.
							break;
						}
					}
				} while(precedingLink != null && links.contains(precedingLink));
				originLinks.add(actual);

				actual = currentLink;
				Link succeedingLink;
				visited.clear();
				do {
					found.add(actual);
					visited.add(actual);
					succeedingLink = getSingleFileSucceedingLink(actual);
					if(succeedingLink != null && links.contains(succeedingLink)) {
						linkSequence.put(actual, succeedingLink);
						actual = succeedingLink;
						
						if (visited.contains(actual)) {
							// We found a closed loop and arrived back at the starting point.
							break;
						}
					}
				} while(succeedingLink != null && links.contains(succeedingLink));
			}
		}

		// find closest link for each single link set
		if(originLinks.size() != 0) {
			for(Link originLink : originLinks) {
				Set<Link> consideredLinks = new HashSet<>();
				double minDist = Double.MAX_VALUE;
				Link actual = originLink;
				do {
					double dist = CoordUtils.distancePointLinesegment(actual.getFromNode().getCoord(), actual.getToNode().getCoord(), coord);
					if(dist < minDist) {
						minDist = dist;
						linksToKeep.put(originLink, actual);
					}
					actual = linkSequence.get(actual);

					if(!consideredLinks.add(actual)) {
						linkSequence.put(actual, null); // loop found, abort
					}
				} while(actual != null);
			}

			// remove links (all succedingLinks are up for removal except the ones closest to the stop)
			for(Link link : singleFileLinks) {
				if(!linksToKeep.containsValue(link)) {
					links.remove(link);
				}
			}
		}

//		NetworkTools.writeNetwork(NetworkTools.createNetworkFromLinks(new HashSet<>(singleFileLinks)), "singleFileLinks.xml");
//		NetworkTools.writeNetwork(NetworkTools.createNetworkFromLinks(new HashSet<>(originLinks)), "originLinks.xml");
//		NetworkTools.writeNetwork(NetworkTools.createNetworkFromLinks(new HashSet<>(linksToKeep.values())), "linksToKeep.xml");
//		NetworkTools.writeNetwork(NetworkTools.createNetworkFromLinks(new HashSet<>(links)), "remainingLinks.xml");
	}

	/**
	 * @return The preceding link if its the given link's only preceding link (ignoring opposite links)
	 * Returns null if there are multiple succeeding links.
	 */
	/*pckg*/ static Link getSingleFilePrecedingLink(Link link) {
		Link oppositeLink = getOppositeLink(link);
		if((link.getFromNode().getInLinks().values().size() == 2
			&& oppositeLink != null)
			||
			(link.getFromNode().getInLinks().values().size() == 1
			&& oppositeLink == null)) {
			for(Link fromInLink : link.getFromNode().getInLinks().values()) {
				if(fromInLink != oppositeLink) {
					return fromInLink;
				}
			}
		}
		return null;
	}

	/**
	 * @return The succeeding link if its the given link's only succeding link (ignoring opposite links).
	 * Returns null if there are multiple succeeding links.
	 */
	/*pckg*/ static Link getSingleFileSucceedingLink(Link link) {
		Link oppositeLink = getOppositeLink(link);
		if((link.getToNode().getOutLinks().values().size() == 2
			&& oppositeLink != null)
			||
			(link.getToNode().getOutLinks().values().size() == 1
			&& oppositeLink == null)) {

			for(Link toOutLink : link.getToNode().getOutLinks().values()) {
				if(toOutLink != oppositeLink) {
					return toOutLink;
				}
			}
		}
		return null;
	}

	/**
	 * Creates a new network from a collection of links. For debugging.
	 */
	public static Network createNetworkFromLinks(Collection<? extends Link> links) {
		Network network = NetworkTools.createNetwork();

		Set<Node> nodes = new HashSet<>();
		for(Link l : links) {
			nodes.add(l.getFromNode());
			nodes.add(l.getToNode());
		}

		for(Node n : nodes) {
			NetworkUtils.createAndAddNode(network, n.getId(), n.getCoord());
		}
		for(Link l : links) {
			network.addLink(network.getFactory().createLink(l.getId(), network.getNodes().get(l.getFromNode().getId()), network.getNodes().get(l.getToNode().getId())));
		}

		return network;
	}

	/**
	 * Calculates the length of a link sequence
	 * @param euclidian uses the sum of all link lengths if <tt>false</tt>
	 */
	public static double calcRouteLength(List<Link> links, boolean euclidian) {
		double length = 0;
		Link debugPrev = null;
		for(Link link : links) {
			if(debugPrev != null && !debugPrev.getToNode().getOutLinks().values().contains(link)) {
				throw new NoSuchElementException("Links not connected!");
			}
			if(euclidian) {
				length += CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());
			} else {
				length += link.getLength();
			}

			debugPrev = link;
		}
		return length;
	}

	public static double calcRouteLength(Network network, TransitRoute transitRoute, boolean euclidian) {
		return calcRouteLength(NetworkUtils.getLinks(network, ScheduleTools.getTransitRouteLinkIds(transitRoute)), euclidian);
	}


	/**
	 * Link filters by mode
	 */
	private static class LinkFilter implements NetworkLinkFilter {

		private final Set<String> modes;

		public LinkFilter(Set<String> modes) {
			this.modes = modes;
		}

		@Override
		public boolean judgeLink(Link l) {
			return MiscUtils.collectionsShareMinOneStringEntry(l.getAllowedModes(), modes);
		}
	}

	private static class InverseLinkFilter implements NetworkLinkFilter {

		private final Set<String> modes;

		public InverseLinkFilter(Set<String> modes) {
			this.modes = modes;
		}

		@Override
		public boolean judgeLink(Link l) {
			return !MiscUtils.collectionsShareMinOneStringEntry(l.getAllowedModes(), modes);
		}
	}

}
