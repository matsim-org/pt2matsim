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
import org.matsim.pt2matsim.mapping.lib.PublicTransitStopImpl;
import org.matsim.pt2matsim.mapping.lib.PublicTransitStop;
import org.matsim.pt2matsim.tools.MiscUtils;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;

import java.util.*;

/**
 * Creates link candidates without mode separated config. Uses more "heuristics".
 *
 * @author polettif
 */
public class LinkCandidateCreatorMagic implements LinkCandidateCreator {

	private static final Set<String> loopLinkModes = CollectionUtils.stringToSet(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE + "," + PublicTransitMappingStrings.STOP_FACILITY_LOOP_LINK);
	protected static Logger log = Logger.getLogger(LinkCandidateCreatorMagic.class);
	private final TransitSchedule schedule;
	private final Network network;
	private final PublicTransitMappingConfigGroup config;

	private final Map<Id<PublicTransitStop>, SortedSet<LinkCandidate>> linkCandidates = new HashMap<>();
	private final Map<Id<PublicTransitStop>, PublicTransitStop> stops = new HashMap<>();
	private final Set<String> scheduleTransportModes = new HashSet<>();


	public LinkCandidateCreatorMagic(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
		this.schedule = schedule;
		this.network = network;
		this.config = config;
	}

	@Override
	public void load() {
		log.info("   search radius: " + config.getNodeSearchRadius());
		log.info("   Note: loop links for stop facilities are created if no link candidate can be found.");

		Map<String, PublicTransitMappingConfigGroup.LinkCandidateCreatorParams> lccParams = config.getLinkCandidateCreatorParams();

		Map<String, Set<Link>> closeLinksMap = new HashMap<>();

		Map<Id<PublicTransitStop>, Set<Link>> candidates = new HashMap<>();

		/*
		  get closest links for each stop facility (separated by mode)
		 */
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleTransportMode = transitRoute.getTransportMode();
				scheduleTransportModes.add(scheduleTransportMode);
				PublicTransitMappingConfigGroup.LinkCandidateCreatorParams param = lccParams.get(scheduleTransportMode);

				TransitRouteStop previousRouteStop = transitRoute.getStops().get(0);

				stops.put(PublicTransitStop.createId(transitLine, transitRoute, previousRouteStop), new PublicTransitStopImpl(transitLine, transitRoute, previousRouteStop));

				Set<Link> tmpCloseLinks = MapUtils.getSet(getCloseLinksKey(transitRoute, previousRouteStop), closeLinksMap);
				if(tmpCloseLinks.size() == 0) {
					tmpCloseLinks.addAll(findClosestLinks(param, previousRouteStop));
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
							closeLinks.addAll(findClosestLinks(param, currentRouteStop));
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
			}
		}

		Map<Id<PublicTransitStop>, Double> maxStopDist = new HashMap<>();
		Map<Id<PublicTransitStop>, Double> minStopDist = new HashMap<>();

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
		}

		/*
		Set priorities
		 */
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
		}

		/*
		  Add manually set link candidates from config
		 */
		addManualLinkCandidates(config.getManualLinkCandidates());
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
	private List<Link> findClosestLinks(PublicTransitMappingConfigGroup.LinkCandidateCreatorParams param, TransitRouteStop routeStop) {
		List<Link> closestLinks = new ArrayList<>();
		Map<Double, Set<Link>> sortedLinks = NetworkTools.findClosestLinks(network, routeStop.getStopFacility().getCoord(), config.getNodeSearchRadius(), param.getNetworkModes());

		// calculate lineSegmentDistance for all links
		double tolFactor = (param.getLinkDistanceTolerance() < 1 ? 0 : param.getLinkDistanceTolerance());
		double maxSoftConstraintDistance = 0.0;

		int nLink = 0;
		for(Map.Entry<Double, Set<Link>> entry : sortedLinks.entrySet()) {
			// if the distance is greate than the maximum distance
			if(entry.getKey() > param.getMaxLinkCandidateDistance()) {
				break;
			}

			// when the link count limit is reached, set the soft constraint distance
			if(nLink < param.getMaxNClosestLinks() && nLink + nLink + entry.getValue().size() >= param.getMaxNClosestLinks()) {
				maxSoftConstraintDistance = entry.getKey() * tolFactor;
			}

			// check if distance is greater than soft constraint distance
			if(nLink + entry.getValue().size() > param.getMaxNClosestLinks() && entry.getKey() > maxSoftConstraintDistance) {
				break;
			}

			// if no loop break has been reached, add link to list
			closestLinks.addAll(entry.getValue());
			nLink += entry.getValue().size();
		}
		return closestLinks;
	}

	private List<Link> findClosestLinks(Coord coord, Set<String> networkModes, int maxLinks, double maxDistance, double tolFactor) {
		List<Link> closestLinks = new ArrayList<>();
		Map<Double, Set<Link>> sortedLinks = NetworkTools.findClosestLinks(network, coord, config.getNodeSearchRadius(), networkModes);

		// calculate lineSegmentDistance for all links
		double maxSoftConstraintDistance = 0.0;

		int nLink = 0;
		for(Map.Entry<Double, Set<Link>> entry : sortedLinks.entrySet()) {
			// if the distance is greate than the maximum distance
			if(entry.getKey() > maxDistance) {
				break;
			}

			// when the link count limit is reached, set the soft constraint distance
			if(nLink < maxLinks && nLink + nLink + entry.getValue().size() >= maxLinks) {
				maxSoftConstraintDistance = entry.getKey() * tolFactor;
			}

			// check if distance is greater than soft constraint distance
			if(nLink + entry.getValue().size() > maxLinks && entry.getKey() > maxSoftConstraintDistance) {
				break;
			}

			// if no loop break has been reached, add link to list
			closestLinks.addAll(entry.getValue());
			nLink += entry.getValue().size();
		}
		return closestLinks;
	}

	/**
	 * Adds the manually set link candidates
	 */
	private void addManualLinkCandidates(Set<PublicTransitMappingConfigGroup.ManualLinkCandidates> manualLinkCandidatesSet) {
		Map<String, Set<PublicTransitMappingConfigGroup.ManualLinkCandidates>> manualCandidatesByMode = new HashMap<>();
		Map<Id<TransitStopFacility>, Set<PublicTransitMappingConfigGroup.ManualLinkCandidates>> manualCandidatesByFacility = new HashMap<>();

		for(PublicTransitMappingConfigGroup.ManualLinkCandidates manualCandidates : manualLinkCandidatesSet) {
			Set<String> scheduleModes = manualCandidates.getScheduleModes();
			if(scheduleModes.size() == 0) {
				scheduleModes = scheduleTransportModes;
			}
			for(String mode : scheduleModes) {
				MapUtils.getSet(mode, manualCandidatesByMode).add(manualCandidates);
			}

			MapUtils.getSet(manualCandidates.getStopFacilityId(), manualCandidatesByFacility).add(manualCandidates);

			// check
			TransitStopFacility parentStopFacility = manualCandidates.getStopFacilityId() != null ? schedule.getFacilities().get(manualCandidates.getStopFacilityId()) : null;
			if(parentStopFacility == null && manualCandidates.getStopFacilityId() != null) {
				log.warn("stopFacility id " + manualCandidates.getStopFacilityId() + " not available in schedule. Manual link candidates for this facility are ignored.");
			}

		}

		if(manualCandidatesByFacility.size() == 0) {
			return;
		}


		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				for(TransitRouteStop routeStop : transitRoute.getStops()) {
					String routeScheduleMode = transitRoute.getTransportMode();
					Set<PublicTransitMappingConfigGroup.ManualLinkCandidates> candidates = manualCandidatesByFacility.get(routeStop.getStopFacility().getId());
					for(PublicTransitMappingConfigGroup.ManualLinkCandidates manualCandidates : candidates) {
						if(manualCandidates.getScheduleModes().contains(routeScheduleMode)) {

							PublicTransitStop ptstop = new PublicTransitStopImpl(transitLine, transitRoute, routeStop);

							PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParams = config.getLinkCandidateCreatorParams().get(routeScheduleMode);

							TransitStopFacility parentStopFacility = schedule.getFacilities().get(manualCandidates.getStopFacilityId());

							SortedSet<LinkCandidate> lcSet = (manualCandidates.doesReplaceCandidates() ? new TreeSet<>() : MiscUtils.getSortedSet(ptstop.getId(), linkCandidates));
							for(Id<Link> linkId : manualCandidates.getLinkIds()) {
								Link link = network.getLinks().get(linkId);
								if(link == null) {
									log.warn("link " + linkId + " not found in network.");
								} else {
									if(CoordUtils.calcEuclideanDistance(link.getCoord(), parentStopFacility.getCoord()) > lccParams.getMaxLinkCandidateDistance()) {
										log.warn("Distance from manual link candidate " + link.getId() + " to stop facility " +
												manualCandidates.getStopFacilityIdStr() + " is more than " + lccParams.getMaxLinkCandidateDistance() +
												"(" + CoordUtils.calcEuclideanDistance(link.getCoord(), parentStopFacility.getCoord()) + ")");
										log.info("Manual link candidate will still be used");
									}

									lcSet.add(new LinkCandidateImpl(link, ptstop));
								}
							}
							linkCandidates.put(ptstop.getId(), lcSet);
						}
					}
				}
			}
		}
	}

	@Override
	public SortedSet<LinkCandidate> getLinkCandidates(TransitRouteStop transitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return linkCandidates.get(PublicTransitStop.createId(transitLine, transitRoute, transitRouteStop));
	}


}