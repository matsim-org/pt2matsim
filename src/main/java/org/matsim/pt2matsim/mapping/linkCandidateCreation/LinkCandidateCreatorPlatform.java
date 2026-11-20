/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2026 by the members listed in the COPYING,        *
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;

/**
 * Link candidate creator that maps stops onto the OSM stop position of their platform track.
 * <p>
 * For transit routes whose schedule transport mode is in {@code platformModes} (typically {@code rail}), the platform
 * code of the stop facility is matched against the {@code local_ref} of OSM stop positions within
 * {@code searchRadius} (network converted with {@link OsmConverterConfigGroup#keepRailStopPositions}). The link
 * candidates are the links ending at the closest matching stop position node, which forces the route over the correct
 * platform track.
 * <p>
 * For all other routes, or if no platform code or no matching stop position is found, the candidates of the delegate
 * (usually {@link LinkCandidateCreatorStandard}) are used.
 * <p>
 * By default, the platform code is read from the stop facility attribute
 * {@link PublicTransitMappingStrings#STOP_FACILITY_ATTRIBUTE_PLATFORM_CODE}.
 */
public class LinkCandidateCreatorPlatform implements LinkCandidateCreator {

	private static final Logger log = LogManager.getLogger(LinkCandidateCreatorPlatform.class);

	private final LinkCandidateCreator delegate;
	private final Map<Id<PublicTransitStop>, SortedSet<LinkCandidate>> candidates = new HashMap<>();

	public LinkCandidateCreatorPlatform(LinkCandidateCreator delegate, TransitSchedule schedule, Network network,
			PublicTransitMappingConfigGroup config) {
		this(delegate, schedule, network, config.getTransportModeAssignment(), config.getPlatformModes(),
				LinkCandidateCreatorPlatform::platformCodeFromAttribute, config.getPlatformSearchRadius());
	}

	/**
	 * @param modeAssignments       schedule transport mode -> allowed network modes
	 * @param platformModes         schedule transport modes for which platform-specific candidates are created
	 * @param platformCodeExtractor returns the platform code of a stop facility, or {@code null}
	 * @param searchRadius          maximal distance [m] between stop facility and OSM stop position
	 */
	public LinkCandidateCreatorPlatform(LinkCandidateCreator delegate, TransitSchedule schedule, Network network,
			Map<String, Set<String>> modeAssignments, Set<String> platformModes,
			Function<TransitStopFacility, String> platformCodeExtractor, double searchRadius) {
		this.delegate = delegate;

		List<Node> stopNodes = new ArrayList<>();
		for (Node node : network.getNodes().values()) {
			if (node.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF) != null) {
				stopNodes.add(node);
			}
		}

		// pre-compute everything, PTMapper queries candidates from multiple threads
		Map<String, List<Link>> linksCache = new HashMap<>();
		int nPlatform = 0;
		int nFallback = 0;
		for (TransitLine line : schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				if (!platformModes.contains(route.getTransportMode())) {
					continue;
				}
				Set<String> networkModes = modeAssignments.getOrDefault(route.getTransportMode(), Set.of(route.getTransportMode()));
				for (TransitRouteStop routeStop : route.getStops()) {
					TransitStopFacility facility = routeStop.getStopFacility();
					List<Link> links = linksCache.computeIfAbsent(facility.getId() + "|" + networkModes,
							k -> stopLinks(facility, networkModes, stopNodes, platformCodeExtractor, searchRadius));
					if (links.isEmpty()) {
						nFallback++;
						continue;
					}
					nPlatform++;
					PublicTransitStop stop = new PublicTransitStopImpl(line, route, routeStop);
					SortedSet<LinkCandidate> set = new TreeSet<>();
					for (Link link : links) {
						set.add(new LinkCandidateImpl(link, stop));
					}
					candidates.put(stop.getId(), set);
				}
			}
		}
		log.info("Platform link candidates: " + nPlatform + " route stops mapped to OSM stop positions, "
				+ nFallback + " use the delegate's candidates.");
	}

	public static String platformCodeFromAttribute(TransitStopFacility facility) {
		Object code = facility.getAttributes().getAttribute(PublicTransitMappingStrings.STOP_FACILITY_ATTRIBUTE_PLATFORM_CODE);
		if (code == null) {
			return null;
		}
		String str = code.toString().trim();
		return str.isEmpty() ? null : str;
	}

	@Override
	public SortedSet<LinkCandidate> getLinkCandidates(TransitRouteStop routeStop, TransitLine line, TransitRoute route) {
		SortedSet<LinkCandidate> set = candidates.get(PublicTransitStop.createId(line, route, routeStop));
		return set != null ? set : delegate.getLinkCandidates(routeStop, line, route);
	}

	private static List<Link> stopLinks(TransitStopFacility facility, Set<String> networkModes, List<Node> stopNodes,
			Function<TransitStopFacility, String> platformCodeExtractor, double searchRadius) {
		String code = platformCodeExtractor.apply(facility);
		if (code == null) {
			return Collections.emptyList();
		}
		Node best = null;
		double bestDist = searchRadius;
		for (Node node : stopNodes) {
			if (!platformMatches(code, (String) node.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF))) {
				continue;
			}
			double d = CoordUtils.calcEuclideanDistance(node.getCoord(), facility.getCoord());
			if (d <= bestDist) {
				best = node;
				bestDist = d;
			}
		}
		if (best == null) {
			return Collections.emptyList();
		}
		List<Link> links = new ArrayList<>();
		for (Link l : best.getInLinks().values()) {
			if (!Collections.disjoint(l.getAllowedModes(), networkModes)) {
				links.add(l);
			}
		}
		return links;
	}

	/**
	 * @param localRef OSM local_ref, may contain multiple values separated by ";"
	 */
	static boolean platformMatches(String code, String localRef) {
		if (localRef == null) {
			return false;
		}
		for (String ref : localRef.split(";")) {
			if (ref.trim().equalsIgnoreCase(code)) {
				return true;
			}
		}
		return false;
	}
}
