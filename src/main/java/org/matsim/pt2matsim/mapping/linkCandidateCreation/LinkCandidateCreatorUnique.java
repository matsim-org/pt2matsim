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
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.CoordTools;
import org.matsim.pt2matsim.tools.MiscUtils;
import org.matsim.pt2matsim.tools.NetworkTools;

import java.util.*;

/**
 * @author polettif
 */
public class LinkCandidateCreatorUnique implements LinkCandidateCreator {

	protected static Logger log = Logger.getLogger(LinkCandidateCreatorStandard.class);

	private static final Set<String> loopLinkModes = CollectionUtils.stringToSet(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE + "," + PublicTransitMappingStrings.STOP_FACILITY_LOOP_LINK);

	private Map<String, PublicTransitMappingConfigGroup.LinkCandidateCreatorParams> lccParams;

	private final TransitSchedule schedule;
	private final Network network;
	private final PublicTransitMappingConfigGroup config;

	private final Map<String, Map<Id<TransitStopFacility>, SortedSet<LinkCandidate>>> linkCandidates = new HashMap<>();
	private Set<ModeStopFacility> modeStopFacilities = new HashSet<>();

	public LinkCandidateCreatorUnique(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
		this.schedule = schedule;
		this.network = network;
		this.config = config;

		createLinkCandidates();
	}


	private void createLinkCandidates() {
		log.info("   search radius: " + config.getNodeSearchRadius());
		log.info("   Note: loop links for stop facilities are created if no link candidate can be found.");

		lccParams = config.getLinkCandidateCreatorParams();

		Map<Link, Map<String, TransitStopFacility>> linkToStopFacility = new HashMap<>();

		/**
		 * get closest links for each stop facility (separated by mode)
		 */
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleTransportMode = transitRoute.getTransportMode();
				for(TransitRouteStop transitRouteStop : transitRoute.getStops()) {

					TransitStopFacility currentStopFacility = transitRouteStop.getStopFacility();
					modeStopFacilities.add(new ModeStopFacility(currentStopFacility, scheduleTransportMode));

					PublicTransitMappingConfigGroup.LinkCandidateCreatorParams param = lccParams.get(scheduleTransportMode);

					List<Link> possibleLinks = new ArrayList<>();

					/**
					 * if stop facility already has a referenced link
					 */
					if(currentStopFacility.getLinkId() != null) {
						possibleLinks.add(network.getLinks().get(currentStopFacility.getLinkId()));
					}
					/**
					 * look for links close to stop facility
					 */
					else {
						List<Link> closestLinks = NetworkTools.findClosestLinks(network,
								transitRouteStop.getStopFacility().getCoord(), config.getNodeSearchRadius(),
								param.getMaxNClosestLinks(), param.getLinkDistanceTolerance(),
								param.getNetworkModes(), param.getMaxLinkCandidateDistance());
						possibleLinks.addAll(closestLinks);
					}

					/**
					 * create artificial link if no links are nearby or stop should use a loop link
					 */
					if(possibleLinks.size() == 0 || param.useArtificialLoopLink()) {
						Link loopLink = createLoopLink(currentStopFacility);
						possibleLinks.add(loopLink);
					}

					for(Link link : possibleLinks) {
						TransitStopFacility previousStopFacility = MapUtils.getMap(link, linkToStopFacility).put(scheduleTransportMode, currentStopFacility);

						// current link has already been assigned to another stopFacility
						if(previousStopFacility != null && !previousStopFacility.getId().equals(currentStopFacility.getId())) {
							double previousDist = CoordTools.distanceStopFacilityToLink(previousStopFacility, link);
							double currentDist = CoordTools.distanceStopFacilityToLink(currentStopFacility, link);
							if(previousDist < currentDist) {
								// undo
								MapUtils.getMap(link, linkToStopFacility).put(scheduleTransportMode, previousStopFacility);
							}
						}
					}
				}
			}
		}

		/**
		 * reverse mapping
		 */
		Map<ModeStopFacility, Set<Link>> stopFacilityToLink = new HashMap<>();
		for(Map.Entry<Link, Map<String, TransitStopFacility>> entry : linkToStopFacility.entrySet()) {
			Link link = entry.getKey();
			for(Map.Entry<String, TransitStopFacility> fac : entry.getValue().entrySet()) {
				ModeStopFacility modeStopFacility = new ModeStopFacility(fac.getValue(), fac.getKey());
				MapUtils.getSet(modeStopFacility, stopFacilityToLink).add(link);
			}
		}

		/**
		 * create and store link candidates
		 */
		for(ModeStopFacility modeStopFacility : modeStopFacilities) {
			Set<Link> links = stopFacilityToLink.get(modeStopFacility);
			TransitStopFacility stopFacility = modeStopFacility.getStopFacility();

			if(links != null) {
				NetworkTools.reduceSequencedLinks(links, stopFacility.getCoord());
			} else {
				// no links for this stop, create artificial loop link
				Link loopLink = createLoopLink(modeStopFacility.getStopFacility());
				links = new HashSet<>();
				links.add(loopLink);
			}

			for(Link link : links) {
				LinkCandidate linkCandidate = new LinkCandidateMode(link, stopFacility, getLinkTravelCost(link), modeStopFacility.getMode());
				MiscUtils.getSortedSet(stopFacility.getId(), MapUtils.getMap(modeStopFacility.getMode(), linkCandidates)).add(linkCandidate);
			}
		}

		/**
		 * Add manually set link candidates from config
		 */
		addManualLinkCandidates(config.getManualLinkCandidates());
	}

	private Link createLoopLink(TransitStopFacility stopFacility) {
		return NetworkTools.createArtificialStopFacilityLink(stopFacility, network, config.getPrefixArtificial(), 20, loopLinkModes);
	}

	private void addManualLinkCandidates(Set<PublicTransitMappingConfigGroup.ManualLinkCandidates> manualLinkCandidatesSet) {
		for(PublicTransitMappingConfigGroup.ManualLinkCandidates manualCandidates : manualLinkCandidatesSet) {
			Set<String> scheduleModes = manualCandidates.getScheduleModes();
			if(scheduleModes.size() == 0) {
				scheduleModes = linkCandidates.keySet();
			}

			TransitStopFacility parentStopFacility = manualCandidates.getStopFacilityId() != null ? schedule.getFacilities().get(manualCandidates.getStopFacilityId()) : null;
			if(parentStopFacility == null && manualCandidates.getStopFacilityId() != null) {
				log.warn("stopFacility id " + manualCandidates.getStopFacilityId() + " not available in schedule. Manual link candidates for this facility are ignored.");
			}

			if(parentStopFacility != null) {
				for(String scheduleMode : scheduleModes) {
					PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParams = config.getLinkCandidateCreatorParams().get(scheduleMode);

					SortedSet<LinkCandidate> lcSet = (manualCandidates.replaceCandidates() ? new TreeSet<>() : MiscUtils.getSortedSet(parentStopFacility.getId(), MapUtils.getMap(scheduleMode, linkCandidates)));
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

							lcSet.add(new LinkCandidateImpl(link, parentStopFacility, getLinkTravelCost(link)));
						}
					}
					MapUtils.getMap(scheduleMode, linkCandidates).put(parentStopFacility.getId(), lcSet);
				}
			}
		}

	}

	@Override
	public SortedSet<LinkCandidate> getLinkCandidates(TransitRouteStop transitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return linkCandidates.get(transitRoute.getTransportMode()).get(transitRouteStop.getStopFacility().getId());
	}

	private double getLinkTravelCost(Link link) {
		return (config.getTravelCostType().equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
	}


	/**
	 * Wrapper class for stop facilities of different schedule transport modes
	 */
	private class ModeStopFacility {

		private final String id;
		private final TransitStopFacility stopFacility;
		private final String mode;

		protected ModeStopFacility(TransitStopFacility stopFacility, String mode) {
			this.id = stopFacility.getId().toString() + ".mode:" + mode;
			this.stopFacility = stopFacility;
			this.mode = mode;
		}

		public String getId() {
			return id;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;

			ModeStopFacility other = (ModeStopFacility) obj;
			return other.getId().equals(this.getId());
		}

		public TransitStopFacility getStopFacility() {
			return stopFacility;
		}

		public String getMode() {
			return mode;
		}

		@Override
		public String toString() {
			return id;
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}
	}

	private class LinkCandidateMode extends LinkCandidateImpl {

		private final String id;
		private final String mode;

		public LinkCandidateMode(Link link, TransitStopFacility parentStopFacility, double linkTravelCost, String mode) {
			super(link, parentStopFacility, linkTravelCost);
			this.id = parentStopFacility.getId().toString() + ".mode:" + mode + ".link:" + link.getId().toString();
			this.mode = mode;
		}

		public LinkCandidateMode() {
			super();
			this.id = "dummy";
			this.mode = null;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;

			LinkCandidate other = (LinkCandidate) obj;
			if(id == null) {
				if(other.getId() != null)
					return false;
			} else if(!id.equals(other.getId()))
				return false;
			return true;
		}

		public int hashCode() {
			return id.hashCode();
		}
	}
}