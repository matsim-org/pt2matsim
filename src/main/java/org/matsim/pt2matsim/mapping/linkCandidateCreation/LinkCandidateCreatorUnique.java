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
import org.matsim.pt2matsim.mapping.UtilsPTMapper;
import org.matsim.pt2matsim.tools.MiscUtils;
import org.matsim.pt2matsim.tools.NetworkTools;

import java.util.*;

/**
 * @author polettif
 */
public class LinkCandidateCreatorUnique implements LinkCandidateCreator {

	private static final Set<String> loopLinkModes = CollectionUtils.stringToSet(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE + "," + PublicTransitMappingStrings.STOP_FACILITY_LOOP_LINK);
	protected static Logger log = Logger.getLogger(LinkCandidateCreatorUnique.class);
	private final TransitSchedule schedule;
	private final Network network;
	private final PublicTransitMappingConfigGroup config;

	private final Map<CandidateKey, SortedSet<LinkCandidate>> linkCandidates = new HashMap<>();
	private final Set<String> scheduleTransportModes = new HashSet<>();


	public LinkCandidateCreatorUnique(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
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

		Map<CandidateKey, Set<Link>> candidates = new HashMap<>();

		/**
		 * get closest links for each stop facility (separated by mode)
		 */
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleTransportMode = transitRoute.getTransportMode();
				scheduleTransportModes.add(scheduleTransportMode);
				PublicTransitMappingConfigGroup.LinkCandidateCreatorParams param = lccParams.get(scheduleTransportMode);

				TransitRouteStop previousRouteStop = transitRoute.getStops().get(0);

				Set<Link> tmpCloseLinks = MapUtils.getSet(getCloseLinksKey(transitRoute, previousRouteStop), closeLinksMap);
				if(tmpCloseLinks.size() == 0) {
					tmpCloseLinks.addAll(findClosestLinks(param, previousRouteStop));
				}

				Set<Link> previousLinks = new HashSet<>(tmpCloseLinks);

				for(int i = 1; i < transitRoute.getStops().size(); i++) {
					TransitStopFacility previousStopFacility = previousRouteStop.getStopFacility();
					TransitRouteStop currentRouteStop = transitRoute.getStops().get(i);
					TransitStopFacility currentStopFacility = currentRouteStop.getStopFacility();

					Set<Link> currentLinks = new HashSet<>();

					/**
					 * if stop facility already has a referenced link
					 */
					if(currentStopFacility.getLinkId() != null) {
						currentLinks.add(network.getLinks().get(currentStopFacility.getLinkId()));
						if(previousLinks.contains(network.getLinks().get(currentStopFacility.getLinkId()))) {
							previousLinks.remove(network.getLinks().get(currentStopFacility.getLinkId()));
						}
					}
					/**
					 * look for links close to stop facility
					 */
					else {
						Set<Link> closeLinks = MapUtils.getSet(getCloseLinksKey(transitRoute, currentRouteStop), closeLinksMap);

						// look for closes links in network
						if(closeLinks.size() == 0) {
							closeLinks.addAll(findClosestLinks(param, currentRouteStop));
						}

						currentLinks.addAll(closeLinks);
					}

					/**
					 * Separate links that belong to two subsequent stops
					 */
					UtilsPTMapper.separateLinks(currentStopFacility.getCoord(), currentLinks, previousStopFacility.getCoord(), previousLinks);

					candidates.put(getKey(transitLine, transitRoute, previousRouteStop), previousLinks);
					candidates.put(getKey(transitLine, transitRoute, currentRouteStop), currentLinks);

					previousLinks = currentLinks;
					previousRouteStop = currentRouteStop;
				}
			}
		}

		Map<String, LinkCandidate> allCandidates = new HashMap<>();

		/**
		 * create and store link candidates
		 */
		for(Map.Entry<CandidateKey, Set<Link>> c : candidates.entrySet()) {
			CandidateKey key = c.getKey();
			Set<Link> links = c.getValue();

			if(links.size() > 0) {
				NetworkTools.reduceSequencedLinks(links, c.getKey().getTransitRouteStop().getStopFacility().getCoord());
			} else {
				// no links for this stop, create artificial loop link
				links = new HashSet<>();
				links.add(createLoopLink(c.getKey().getTransitRouteStop().getStopFacility()));
			}

			for(Link link : links) {
				LinkCandidate linkCandidate =
						allCandidates.computeIfAbsent(c.getKey().getTransitRouteStop().getStopFacility().getId().toString() + ":" + link.getId().toString(),
								k -> new LinkCandidateImpl(link, c.getKey().getTransitRouteStop().getStopFacility()));
				MiscUtils.getSortedSet(getKey(key.getTransitLine(), key.getTransitRoute(), key.getTransitRouteStop()), linkCandidates).add(linkCandidate);
			}
		}

		/**
		 * Add manually set link candidates from config
		 */
		addManualLinkCandidates(config.getManualLinkCandidates());
	}

	private String getCloseLinksKey(TransitRoute transitRoute, TransitRouteStop routeStop) {
		return transitRoute.getTransportMode() + ":" + routeStop.getStopFacility().getId();
	}

	private CandidateKey getKey(TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop transitRouteStop) {
		return new CandidateKey(transitLine, transitRoute, transitRouteStop);
	}

	private Link createLoopLink(TransitStopFacility stopFacility) {
		return NetworkTools.createArtificialStopFacilityLink(stopFacility, network, PublicTransitMappingStrings.PREFIX_ARTIFICIAL, 20, loopLinkModes);
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

							PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParams = config.getLinkCandidateCreatorParams().get(routeScheduleMode);

							TransitStopFacility parentStopFacility = schedule.getFacilities().get(manualCandidates.getStopFacilityId());

							SortedSet<LinkCandidate> lcSet = (manualCandidates.doesReplaceCandidates() ? new TreeSet<>() : MiscUtils.getSortedSet(getKey(transitLine, transitRoute, routeStop), linkCandidates));
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

									lcSet.add(new LinkCandidateImpl(link, parentStopFacility));
								}
							}
							linkCandidates.put(getKey(transitLine, transitRoute, routeStop), lcSet);
						}
					}
				}
			}
		}
	}

	@Override
	public SortedSet<LinkCandidate> getLinkCandidates(TransitRouteStop transitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return linkCandidates.get(getKey(transitLine, transitRoute, transitRouteStop));
	}

	private double getLinkTravelCost(Link link) {
		return (config.getTravelCostType().equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
	}

	private class CandidateKey {

		private final String key;
		private final TransitLine transitLine;
		private final TransitRoute transitRoute;
		private final TransitRouteStop transitRouteStop;

		public CandidateKey(TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop transitRouteStop) {
			this.key = "line:" + transitLine.getId() +
					".route:" + transitRoute.getId() +
					".time:" + transitRouteStop.getArrivalOffset() +
					".stop:" + transitRouteStop.getStopFacility().getId();

			this.transitLine = transitLine;
			this.transitRoute = transitRoute;
			this.transitRouteStop = transitRouteStop;
		}

		public String getKey() {
			return key;
		}

		public TransitLine getTransitLine() {
			return transitLine;
		}

		public TransitRoute getTransitRoute() {
			return transitRoute;
		}

		public TransitRouteStop getTransitRouteStop() {
			return transitRouteStop;
		}

		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(obj.getClass() != this.getClass())
				return false;

			CandidateKey other = (CandidateKey) obj;
			return this.getKey().equals(other.getKey());
		}

		public int hashCode() {
			return key.hashCode();
		}

		public String toString() {
			return key;
		}

	}
}