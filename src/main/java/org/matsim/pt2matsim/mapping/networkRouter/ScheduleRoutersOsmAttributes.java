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

package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public class ScheduleRoutersOsmAttributes implements ScheduleRouters {


    protected static Logger log = Logger.getLogger(ScheduleRoutersGtfsShapes.class);
    /**
     * If a link has a route with the same transport mode as the transit route,
     * the link's travel cost is multiplied by this factor.
     */
    private final double osmPtLinkTravelCostFactor;
    // standard fields
    private final TransitSchedule schedule;
    private final Network network;
    private final Map<String, Set<String>> transportModeAssignment;
    private final PublicTransitMappingConfigGroup.TravelCostType travelCostType;


    // path calculators
    private final Map<String, PathCalculator> pathCalculatorsByMode = new HashMap<>();
    private final Map<String, Network> networksByMode = new HashMap<>();
    private final Map<String, OsmRouter> osmRouters = new HashMap<>();


    public ScheduleRoutersOsmAttributes(TransitSchedule schedule, Network network, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType travelCostType, double osmPtLinkTravelCostFactor) {
        this.transportModeAssignment = transportModeAssignment;
        this.travelCostType = travelCostType;
        this.schedule = schedule;
        this.network = network;
        this.osmPtLinkTravelCostFactor = osmPtLinkTravelCostFactor;

        load();
    }

    /**
     * Load path calculators for all transit routes
     */
    private void load() {
        log.info("Initiating network and router for transit routes...");
        LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory();
        for (TransitLine transitLine : schedule.getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                String scheduleMode = transitRoute.getTransportMode();
                PathCalculator tmpRouter = pathCalculatorsByMode.get(scheduleMode);
                if (tmpRouter == null) {
                    log.info("New router for schedule mode " + scheduleMode);
                    Set<String> networkTransportModes = transportModeAssignment.get(scheduleMode);

                    Network filteredNetwork = NetworkTools.createFilteredNetworkByLinkMode(this.network, networkTransportModes);

                    OsmRouter r = new OsmRouter(scheduleMode);

                    tmpRouter = new PathCalculator(factory.createPathCalculator(filteredNetwork, r, r));

                    pathCalculatorsByMode.put(scheduleMode, tmpRouter);
                    networksByMode.put(scheduleMode, filteredNetwork);
                    osmRouters.put(scheduleMode, r);
                }
            }
        }
    }


    @Override
    public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute) {
        return this.calcLeastCostPath(fromLinkCandidate.getLink().getToNode().getId(), toLinkCandidate.getLink().getFromNode().getId(), transitLine, transitRoute);
    }

    @Override
    public LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId, TransitLine transitLine, TransitRoute transitRoute) {
        Network n = networksByMode.get(transitRoute.getTransportMode());
        Node fromNode = n.getNodes().get(fromNodeId);
        Node toNode = n.getNodes().get(toNodeId);

        if (fromNode != null && toNode != null) {
            return pathCalculatorsByMode.get(transitRoute.getTransportMode()).calcPath(fromNode, toNode);
        } else {
            return null;
        }
    }

    @Override
    public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
        return PTMapperTools.calcMinTravelCost(fromTransitRouteStop, toTransitRouteStop, travelCostType);
    }

    @Override
    public double getLinkCandidateTravelCost(LinkCandidate candidate) {
        return osmRouters.get(candidate.getStop().getTransitRoute().getTransportMode()).calcLinkTravelCost(candidate.getLink());
    }

    /**
     * Class is sent to path calculator factory
     */
    private class OsmRouter implements TravelDisutility, TravelTime {

        private final String scheduleMode;

        public OsmRouter(String scheduleMode) {
            this.scheduleMode = scheduleMode;
        }

        private double calcLinkTravelCost(Link link) {
            double travelCost = PTMapperTools.calcTravelCost(link, travelCostType);

            Attributes attributes = network.getLinks().get(link.getId()).getAttributes();
            Set<String> routeMaster = CollectionUtils.stringToSet((String) attributes.getAttribute(OsmConverterConfigGroup.LINK_ATTRIBUTE_RELATION_ROUTE_MASTER));
            Set<String> route = CollectionUtils.stringToSet((String) attributes.getAttribute(OsmConverterConfigGroup.LINK_ATTRIBUTE_RELATION_ROUTE));

            if (route.contains(scheduleMode) || routeMaster.contains(scheduleMode)) {
                travelCost *= osmPtLinkTravelCostFactor;
            }

            return travelCost;
        }

        @Override
        public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
            return calcLinkTravelCost(link);
        }

        @Override
        public double getLinkMinimumTravelDisutility(Link link) {
            return calcLinkTravelCost(link);
        }

        @Override
        public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
            return link.getLength() / link.getFreespeed();
        }
    }

    public static class Factory implements ScheduleRoutersFactory {
    	final private TransitSchedule schedule;
    	final private Network network;
    	final private Map<String, Set<String>> transportModeAssignment;
    	final private PublicTransitMappingConfigGroup.TravelCostType travelCostType;
    	final private double osmPtLinkTravelCostFactor;
    	
    	public Factory(TransitSchedule schedule, Network network, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType travelCostType, double osmPtLinkTravelCostFactor) {
    		this.schedule = schedule;
    		this.network = network;
    		this.transportModeAssignment = transportModeAssignment;
    		this.travelCostType = travelCostType;
    		this.osmPtLinkTravelCostFactor = osmPtLinkTravelCostFactor;
    	}

		@Override
		public ScheduleRouters createInstance() {
			return new ScheduleRoutersOsmAttributes(schedule, network, transportModeAssignment, travelCostType, osmPtLinkTravelCostFactor);
		}
    }
}
