package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.*;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.MapperModule;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a Router for each transportMode of a schedule.
 *
 * @author polettif
 */
public class ScheduleRoutersTransportMode implements ScheduleRouters, MapperModule {

	protected static Logger log = Logger.getLogger(ScheduleRoutersTransportMode.class);

	// standard fields
	private final PublicTransitMappingConfigGroup config;
	private final TransitSchedule schedule;
	private final Network network;

	// path calculators
	private final Map<String, LeastCostPathCalculator> pathCalculatorsByMode = new HashMap<>();
	private final Map<String, Network> networksByMode = new HashMap<>();

	public ScheduleRoutersTransportMode(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network) {
		this.config = config;
		this.schedule = schedule;
		this.network = network;
	}

	/**
	 * Load path calculators for all transit routes
	 */
	@Override
	public void load() {
		Map<String, Set<String>> modeRoutingAssignment = config.getModeRoutingAssignment();

		log.info("Initiating network and router for transit routes...");
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleMode = transitRoute.getTransportMode();
				LeastCostPathCalculator tmpRouter = pathCalculatorsByMode.get(scheduleMode);
				if(tmpRouter == null) {
					log.info("New router for schedule mode " + scheduleMode);
					Set<String> networkTransportModes = modeRoutingAssignment.get(scheduleMode);

					Network filteredNetwork = NetworkTools.createFilteredNetworkByLinkMode(this.network, networkTransportModes);

					LocalRouter r = new LocalRouter();

					LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory(filteredNetwork, r);
					tmpRouter = factory.createPathCalculator(filteredNetwork, r, r);

					pathCalculatorsByMode.put(scheduleMode, tmpRouter);
					networksByMode.put(scheduleMode, filteredNetwork);
				}
			}
		}
	}


	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute) {
		return this.calcLeastCostPath(fromLinkCandidate.getToNodeId(), toLinkCandidate.getFromNodeId(), transitLine, transitRoute);
	}

	@Override
	public synchronized LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId, TransitLine transitLine, TransitRoute transitRoute) {
		// Synchronized since the used Dijkstra algorithm is not thread safe
		Network n = networksByMode.get(transitRoute.getTransportMode());
		Node fromNode = n.getNodes().get(fromNodeId);
		Node toNode = n.getNodes().get(toNodeId);

		if(fromNode != null && toNode != null) {
			return pathCalculatorsByMode.get(transitRoute.getTransportMode()).calcLeastCostPath(fromNode, toNode, 0, null, null);
		} else {
			return null;
		}
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return PTMapperTools.calcTravelCost(fromTransitRouteStop, toTransitRouteStop, config.getTravelCostType());
	}

	@Override
	public double getLinkCandidateTravelCost(TransitLine transitLine, TransitRoute transitRoute, LinkCandidate linkCandidateCurrent) {
		return PTMapperTools.calcTravelCost(linkCandidateCurrent.getLink(), config.getTravelCostType());
	}

	/**
	 * Class is sent to path calculator factory
	 */
	private class LocalRouter implements TravelDisutility, TravelTime{

		@Override
		public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
			return this.getLinkMinimumTravelDisutility(link);
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return PTMapperTools.calcTravelCost(link, config.getTravelCostType());
		}

		@Override
		public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
			return link.getLength() / link.getFreespeed();
		}
	}
}
