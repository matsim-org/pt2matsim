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
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a Router for each transportMode of a schedule.
 * <p>
 * Default ScheduleRouters
 *
 * @author polettif
 */
public class ScheduleRoutersStandard implements ScheduleRouters {

	protected static Logger log = Logger.getLogger(ScheduleRoutersStandard.class);

	// standard fields
	private final TransitSchedule schedule;
	private final Network network;
	private final Map<String, Set<String>> transportModeAssignment;
	private final PublicTransitMappingConfigGroup.TravelCostType travelCostType;

	// path calculators
	private final Map<String, PathCalculator> pathCalculatorsByMode = new HashMap<>();
	private final Map<String, Network> networksByMode = new HashMap<>();
	private final boolean considerCandidateDist;

	private ScheduleRoutersStandard(TransitSchedule schedule, Network network, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType costType, boolean routingWithCandidateDistance) {
		this.schedule = schedule;
		this.network = network;
		this.transportModeAssignment = transportModeAssignment;
		this.travelCostType = costType;
		this.considerCandidateDist = routingWithCandidateDistance;

		load();
	}

	/**
	 * Load path calculators for all transit routes
	 */
	private void load() {
		log.info("==============================================");
		log.info("Creating network routers for transit routes...");
		log.info("Initiating network and router for transit routes...");
		LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory();
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleMode = transitRoute.getTransportMode();
				PathCalculator tmpRouter = pathCalculatorsByMode.get(scheduleMode);
				if(tmpRouter == null) {
					log.info("New router for schedule mode " + scheduleMode);
					Set<String> networkTransportModes = transportModeAssignment.get(scheduleMode);

					Network filteredNetwork = NetworkTools.createFilteredNetworkByLinkMode(this.network, networkTransportModes);

					LocalRouter r = new LocalRouter();

					tmpRouter = new PathCalculator(factory.createPathCalculator(filteredNetwork, r, r));

					pathCalculatorsByMode.put(scheduleMode, tmpRouter);
					networksByMode.put(scheduleMode, filteredNetwork);
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
		if(n == null) return null;

		Node fromNode = n.getNodes().get(fromNodeId);
		Node toNode = n.getNodes().get(toNodeId);
		if(fromNode == null || toNode == null) return null;

		return pathCalculatorsByMode.get(transitRoute.getTransportMode()).calcPath(fromNode, toNode);
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		double minTC = PTMapperTools.calcMinTravelCost(fromTransitRouteStop, toTransitRouteStop, travelCostType);
		if(minTC == 0)
			minTC = CoordUtils.calcEuclideanDistance(fromTransitRouteStop.getStopFacility().getCoord(), toTransitRouteStop.getStopFacility().getCoord()) / 10;
		return minTC;
	}

	@Override
	public double getLinkCandidateTravelCost(LinkCandidate linkCandidateCurrent) {
		double dist = 0;
		if(considerCandidateDist) {
			dist += (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? linkCandidateCurrent.getStopFacilityDistance() / linkCandidateCurrent.getLink().getFreespeed() : linkCandidateCurrent.getStopFacilityDistance());
			dist *= 2;
		}
		return dist + PTMapperTools.calcTravelCost(linkCandidateCurrent.getLink(), travelCostType);
	}

	/**
	 * Class is sent to path calculator factory
	 */
	private class LocalRouter implements TravelDisutility, TravelTime {

		@Override
		public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
			return this.getLinkMinimumTravelDisutility(link);
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return PTMapperTools.calcTravelCost(link, travelCostType);
		}

		@Override
		public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
			return link.getLength() / link.getFreespeed();
		}
	}

	/**
	 * Factory for a ScheduleRoutersStandard instance
	 */
	public static class Factory implements ScheduleRoutersFactory {
		private final TransitSchedule schedule;
		private final Network network;
		private final Map<String, Set<String>> transportModeAssignment;
		private final PublicTransitMappingConfigGroup.TravelCostType costType;
		private boolean routingWithCandidateDistance;
		
		public Factory(TransitSchedule schedule, Network network, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType costType, boolean routingWithCandidateDistance) {
			this.schedule = schedule;
			this.network = network;
			this.transportModeAssignment = transportModeAssignment;
			this.costType = costType;
			this.routingWithCandidateDistance = routingWithCandidateDistance;
		}
		
		public Factory(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
			this(schedule, network, config.getTransportModeAssignment(), config.getTravelCostType(), config.getRoutingWithCandidateDistance());
		}

		@Override
		public ScheduleRouters createInstance() {
			return new ScheduleRoutersStandard(schedule, network, transportModeAssignment, costType, routingWithCandidateDistance);
		}

	}
}
