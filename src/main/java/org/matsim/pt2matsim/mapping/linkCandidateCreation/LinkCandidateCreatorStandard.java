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

package org.matsim.pt2matsim.mapping.linkCandidateCreation;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.Progress;
import org.matsim.pt2matsim.tools.MiscUtils;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;

import java.util.*;

/**
 * Creates link candidates without mode separated config. Uses more "heuristics".
 *
 * @author polettif
 */
public class LinkCandidateCreatorStandard implements LinkCandidateCreator {

	private static final Set<String> loopLinkModes = CollectionUtils.stringToSet(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE + "," + PublicTransitMappingStrings.STOP_FACILITY_LOOP_LINK);
	protected static Logger log = Logger.getLogger(LinkCandidateCreatorStandard.class);

	private final TransitSchedule schedule;
	private final Network network;

	private final Map<Id<PublicTransitStop>, SortedSet<LinkCandidate>> linkCandidates = new HashMap<>();
	private final Map<Id<PublicTransitStop>, PublicTransitStop> stops = new HashMap<>();

	private final int nLinks;
	private final double distanceMultiplier;
	private final double maxDistance;
	private final Map<String, Set<String>> transportModeAssignments;
	private double nodeSearchRadius;


	public LinkCandidateCreatorStandard(TransitSchedule schedule, Network network, int nLinks, double distanceMultiplier, double maxDistance, Map<String, Set<String>> transportModeAssignments) {
		this.schedule = schedule;
		this.network = network;
		this.nLinks = nLinks;
		this.distanceMultiplier = distanceMultiplier;
		this.maxDistance = maxDistance;
		this.transportModeAssignments = transportModeAssignments;

		load();
	}

	public LinkCandidateCreatorStandard(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
		this(schedule, network, config.getNLinkThreshold(), config.getCandidateDistanceMultiplier(), config.getMaxLinkCandidateDistance(), config.getTransportModeAssignment());
	}

	private void load() {
		this.nodeSearchRadius = maxDistance*15;

		log.info("===========================");
		log.info("Creating link candidates...");
		log.info("   search radius: " + nodeSearchRadius);
		log.info("   Note: loop links for stop facilities are created if no link candidate can be found.");

		Map<String, Set<Link>> closeLinksMap = new HashMap<>();

		Map<Id<PublicTransitStop>, Set<Link>> candidates = new HashMap<>();
		
		long totalNumberOfRoutes = 0;
		
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			totalNumberOfRoutes += transitLine.getRoutes().size();
		}
		
		Progress progress = new Progress(totalNumberOfRoutes, "Getting closest links ...");

		/*
		  get closest links for each stop facility (separated by mode)
		 */
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleTransportMode = transitRoute.getTransportMode();
				Set<String> networkModes = transportModeAssignments.get(scheduleTransportMode);

				// If no transportModes have been defined in the config, no links should be found by findClosestLink (which requires an empty set)
				if(networkModes == null) {
					log.warn("No transportModeAssignment found for schedule mode " + scheduleTransportMode);
					networkModes = new HashSet<>();
					transportModeAssignments.put(scheduleTransportMode, networkModes);
				}

				TransitRouteStop previousRouteStop = transitRoute.getStops().get(0);

				stops.put(PublicTransitStop.createId(transitLine, transitRoute, previousRouteStop), new PublicTransitStopImpl(transitLine, transitRoute, previousRouteStop));

				Set<Link> tmpCloseLinks = MapUtils.getSet(getCloseLinksKey(transitRoute, previousRouteStop), closeLinksMap);
				if(tmpCloseLinks.size() == 0) {
					tmpCloseLinks.addAll(findClosestLinks(previousRouteStop.getStopFacility().getCoord(), networkModes));
				}

				Set<Link> previousLinks = new HashSet<>(tmpCloseLinks);

				for(int i = 1; i < transitRoute.getStops().size(); i++) {
					TransitStopFacility previousStopFacility = previousRouteStop.getStopFacility();
					TransitRouteStop currentRouteStop = transitRoute.getStops().get(i);
					TransitStopFacility currentStopFacility = currentRouteStop.getStopFacility();
					stops.put(PublicTransitStop.createId(transitLine, transitRoute, currentRouteStop), new PublicTransitStopImpl(transitLine, transitRoute, currentRouteStop));

					Set<Link> currentLinks = new HashSet<>();

					/*
					  if stop facility already has a referenced link
					 */
					if(currentStopFacility.getLinkId() != null) {
						currentLinks.add(network.getLinks().get(currentStopFacility.getLinkId()));
						if(previousLinks.contains(network.getLinks().get(currentStopFacility.getLinkId()))) {
							previousLinks.remove(network.getLinks().get(currentStopFacility.getLinkId()));
						}
					}
					/*
					  look for links close to stop facility
					 */
					else {
						Set<Link> closeLinks = MapUtils.getSet(getCloseLinksKey(transitRoute, currentRouteStop), closeLinksMap);

						// look for closes links in network
						if(closeLinks.size() == 0) {
							closeLinks.addAll(findClosestLinks(currentRouteStop.getStopFacility().getCoord(), networkModes));
						}

						currentLinks.addAll(closeLinks);
					}

					/*
					  Separate links that belong to two subsequent stops
					 */
					PTMapperTools.separateLinks(currentStopFacility.getCoord(), currentLinks, previousStopFacility.getCoord(), previousLinks);

					candidates.put(PublicTransitStop.createId(transitLine, transitRoute, previousRouteStop), previousLinks);
					candidates.put(PublicTransitStop.createId(transitLine, transitRoute, currentRouteStop), currentLinks);

					previousLinks = currentLinks;
					previousRouteStop = currentRouteStop;
				}
				
				progress.update();
			}
		}

		Map<Id<PublicTransitStop>, Double> maxStopDist = new HashMap<>();
		Map<Id<PublicTransitStop>, Double> minStopDist = new HashMap<>();
		
		progress = new Progress(candidates.size(), "Creating link candidates ...");

		/*
		  create and store link candidates
		 */
		for(Map.Entry<Id<PublicTransitStop>, Set<Link>> c : candidates.entrySet()) {
			PublicTransitStop stop = stops.get(c.getKey());
			Set<Link> links = c.getValue();

			if(links.size() > 0) {
				NetworkTools.reduceSequencedLinks(links, stop.getStopFacility().getCoord());
			} else {
				// no links for this stop, create artificial loop link
				links = new HashSet<>();
				links.add(createLoopLink(stop.getStopFacility()));
			}

			double minDist = Double.MAX_VALUE;
			double maxDist = 0.0;

			for(Link link : links) {
				LinkCandidate linkCandidate = new LinkCandidateImpl(link, stop);
				MiscUtils.getSortedSet(stop.getId(), linkCandidates).add(linkCandidate);

				if(linkCandidate.getStopFacilityDistance() > maxDist) maxDist = linkCandidate.getStopFacilityDistance();
				if(linkCandidate.getStopFacilityDistance() < minDist) minDist = linkCandidate.getStopFacilityDistance();
			}

			maxStopDist.put(stop.getId(), maxDist);
			minStopDist.put(stop.getId(), minDist);
			
			progress.update();
		}

		/*
		Set priorities
		 */
		int nLC = 0;
		progress = new Progress(linkCandidates.size(), "Setting candidate priorities ...");
		
		for(Map.Entry<Id<PublicTransitStop>, SortedSet<LinkCandidate>> entry : linkCandidates.entrySet()) {
			double minDist = minStopDist.get(entry.getKey());
			double maxDist = maxStopDist.get(entry.getKey());
			double delta = maxDist - minDist;

			for(LinkCandidate candidate : entry.getValue()) {
				double d = candidate.getStopFacilityDistance();
				if(delta > 0) {
					candidate.setPriority(1 - ((d - minDist) / (maxDist - minDist)));
				} else {
					candidate.setPriority(1);
				}
			}
			nLC += entry.getValue().size();
		}
		log.info("Average number of link candidates: " + nLC / linkCandidates.size());
	}

	private String getCloseLinksKey(TransitRoute transitRoute, TransitRouteStop routeStop) {
		return transitRoute.getTransportMode() + ":" + routeStop.getStopFacility().getId();
	}

	private Link createLoopLink(TransitStopFacility stopFacility) {
		return PTMapperTools.createArtificialStopFacilityLink(stopFacility, network, PublicTransitMappingStrings.PREFIX_ARTIFICIAL, 20, loopLinkModes);
	}

	/**
	 * Looks for nodes within search radius of <tt>coord</tt> (using {@link NetworkUtils#getNearestNodes},
	 * fetches all in- and outlinks and sorts them ascending by their
	 * distance to the coordinates given.
	 * <p/>
	 * The method then returns all links within <tt>maxLinkDistance</tt> or <tt>maxNLinks</tt>*
	 * whichever is reached earlier. Links with the same distance (i.e. opposite links) are always returned.
	 * <p/>
	 * * Note: maxNLinks is not a hard constraint. This method returns more than maxNLinks links if two links
	 * have the same distance to the facility. It also returns more than maxNLinks if toleranceFactor is > 1.
	 * <p/>
	 * Distance Link to Coordinate is calculated using {@link CoordUtils#distancePointLinesegment}).
	 *
	 * The abort conditions are ordered as follows:
	 * <ol>
	 *     <li>distance > maxLinkCandidateDistance</li>
	 *     <li>distance > (distance of the maxNLinks-th link * toleranceFactor)</li>
	 * </ol>
	 *
	 * @return list of the closest links from coordinate <tt>coord</tt>.
	 */
	private List<Link> findClosestLinks(Coord coord, Set<String> networkModes) {
		List<Link> closestLinks = new ArrayList<>();
		Map<Double, Set<Link>> sortedLinks = NetworkTools.findClosestLinks(network, coord, nodeSearchRadius, networkModes);

		double distanceThreshold = this.maxDistance;
		int nLink = 0;

		for(Map.Entry<Double, Set<Link>> entry : sortedLinks.entrySet()) {
			double currentDistance = entry.getKey();
			double currentNLinks = entry.getValue().size();

			// if the distance is greater than the maximum distance
			if(currentDistance > maxDistance) {
				break;
			}

			// when the link count limit is reached, set the soft constraint distance
			if(nLink < this.nLinks && nLink + currentNLinks >= this.nLinks) {
				distanceThreshold = currentDistance * this.distanceMultiplier;
			}

			// check if distance is greater than soft constraint distance
			if(nLink + currentNLinks > this.nLinks && currentDistance > distanceThreshold) {
				break;
			}

			// if no loop break has been reached, add link to list
			closestLinks.addAll(entry.getValue());
			nLink += entry.getValue().size();
		}
		return closestLinks;
	}

	@Override
	public SortedSet<LinkCandidate> getLinkCandidates(TransitRouteStop transitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return linkCandidates.get(PublicTransitStop.createId(transitLine, transitRoute, transitRouteStop));
	}

}