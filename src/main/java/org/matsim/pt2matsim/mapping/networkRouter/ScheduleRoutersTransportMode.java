package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.*;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.MapperModule;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
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

	private final PublicTransitMappingConfigGroup config;
	private final TransitSchedule schedule;
	private final Network network;
	private final Map<TransitLine, Map<TransitRoute, LeastCostPathCalculator>> routers = new HashMap<>();
	private final Map<String, LeastCostPathCalculator> routersByMode = new HashMap<>();

	private final LocalRouter genericRouter;
	private Map<String, Network> modeNetworks = new HashMap<>();

	public ScheduleRoutersTransportMode(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network) {
		this.config = config;
		this.schedule = schedule;
		this.network = network;

		this.genericRouter = new LocalRouter(config.getTravelCostType());
	}

	@Override
	public void load() {
		/**
		 * Initialize routers
		 */
		Map<String, Set<String>> modeRoutingAssignment = config.getModeRoutingAssignment();

		FastAStarRouter.setTravelCostType(config.getTravelCostType());

		log.info("Initiating network and router for transit routes...");
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleMode = transitRoute.getTransportMode();
				LeastCostPathCalculator tmpRouter = routersByMode.get(scheduleMode);
				if(tmpRouter == null) {
					log.info("New router for schedule mode " + scheduleMode);
					Set<String> networkTransportModes = modeRoutingAssignment.get(scheduleMode);

					Network filteredNetwork = NetworkTools.createFilteredNetworkByLinkMode(this.network, networkTransportModes);

					LocalRouter r = new LocalRouter(config.getTravelCostType());

					LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory(filteredNetwork, r);
					tmpRouter = factory.createPathCalculator(filteredNetwork, r, r);

					routersByMode.put(scheduleMode, tmpRouter);
					modeNetworks.put(scheduleMode, filteredNetwork);
				}
				MapUtils.getMap(transitLine, routers).put(transitRoute, tmpRouter);
			}
		}
	}

	/**
	 * Either extract the router or call this method
	 */
	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute) {
		return this.calcLeastCostPath(fromLinkCandidate.getToNodeId(), toLinkCandidate.getFromNodeId(), transitLine, transitRoute);
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId, TransitLine transitLine, TransitRoute transitRoute) {

		Network n = modeNetworks.get(transitRoute.getTransportMode());
		Node fromNode = n.getNodes().get(fromNodeId);
		Node toNode = n.getNodes().get(toNodeId);

		if(fromNode != null && toNode != null) {
			return routers.get(transitLine).get(transitRoute).calcLeastCostPath(fromNode, toNode, 0, null, null);
		} else {
			return null;
		}
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		double travelTime = (toTransitRouteStop.getArrivalOffset() - fromTransitRouteStop.getDepartureOffset());
		double beelineDistance = CoordUtils.calcEuclideanDistance(fromTransitRouteStop.getStopFacility().getCoord(), toTransitRouteStop.getStopFacility().getCoord());

		if(config.getTravelCostType().equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime)) {
			return travelTime;
		} else {
			return beelineDistance;
		}
	}

	@Override
	public double getLinkTravelCost(TransitLine transitLine, TransitRoute transitRoute, LinkCandidate linkCandidateCurrent) {
		return genericRouter.getLinkMinimumTravelDisutility(linkCandidateCurrent.getLink());
	}

	private class LocalRouter implements TravelDisutility, TravelTime{

		private final PublicTransitMappingConfigGroup.TravelCostType type;

		public LocalRouter(PublicTransitMappingConfigGroup.TravelCostType travelCostType) {
			this.type = travelCostType;
		}

		@Override
		public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
			return this.getLinkMinimumTravelDisutility(link);
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return (type.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
		}

		@Override
		public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
			return link.getLength() / link.getFreespeed();
		}
	}
}
