package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a router for each transportMode of a schedule.
 *
 * @author polettif
 */
public class ScheduleRoutersTransportMode implements ScheduleRouters {

	protected static Logger log = Logger.getLogger(ScheduleRoutersTransportMode.class);

	private final PublicTransitMappingConfigGroup config;
	private final TransitSchedule schedule;
	private final Network network;
	private final boolean useArtificial;
	private final Map<TransitLine, Map<TransitRoute, Router>> routers = new HashMap<>();
	private final Map<String, Router> routersByMode = new HashMap<>();

	public ScheduleRoutersTransportMode(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network, boolean useArtificial) {
		this.config = config;
		this.schedule = schedule;
		this.network = network;
		this.useArtificial = useArtificial;

		init();
	}

	public ScheduleRoutersTransportMode(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network) {
		this.config = config;
		this.schedule = schedule;
		this.network = network;
		this.useArtificial = false;

		init();
	}

	private void init() {
		/**
		 * Initialize routers
		 */
		Map<String, Set<String>> modeRoutingAssignment = config.getModeRoutingAssignment();

		FastAStarRouter.setTravelCostType(config.getTravelCostType());

		log.info("Initiating network and router for transit routes...");
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				String scheduleMode = transitRoute.getTransportMode();
				Router tmpRouter = routersByMode.get(scheduleMode);
				if(tmpRouter == null) {
					log.info("New router for schedule mode " + scheduleMode);
					Set<String> networkTransportModes = modeRoutingAssignment.get(scheduleMode);

					if(useArtificial) networkTransportModes.add(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE);

					tmpRouter = new FastAStarRouter(NetworkTools.createFilteredNetworkByLinkMode(network, networkTransportModes));
					routersByMode.put(scheduleMode, tmpRouter);
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
		return routers.get(transitLine).get(transitRoute).calcLeastCostPath(fromLinkCandidate.getToNodeId(), toLinkCandidate.getFromNodeId());
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId, TransitLine transitLine, TransitRoute transitRoute) {
		return routers.get(transitLine).get(transitRoute).calcLeastCostPath(fromNodeId, toNodeId);
	}

	@Override
	public Router getRouter(TransitLine transitLine, TransitRoute transitRoute) {
		String scheduleTransportMode = transitRoute.getTransportMode();

		return routersByMode.get(scheduleTransportMode);
	}

	@Override
	public Router getRouter(String scheduleMode) {
		return routersByMode.get(scheduleMode);
	}

	/*
	 * TODO move methods from routers to schedulerouter
	 */

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return routers.get(transitLine).get(transitRoute).getMinimalTravelCost(fromTransitRouteStop, toTransitRouteStop);
	}

	@Override
	public double getArtificialLinkFreeSpeed(double maxAllowedTravelCost, LinkCandidate linkCandidateCurrent, LinkCandidate linkCandidateNext, TransitLine transitLine, TransitRoute transitRoute) {
		return routers.get(transitLine).get(transitRoute).getArtificialLinkFreeSpeed(maxAllowedTravelCost, linkCandidateCurrent, linkCandidateNext);
	}

	@Override
	public double getArtificialLinkLength(double maxAllowedTravelCost, LinkCandidate linkCandidateCurrent, LinkCandidate linkCandidateNext, TransitLine transitLine, TransitRoute transitRoute) {
		return routers.get(transitLine).get(transitRoute).getArtificialLinkFreeSpeed(maxAllowedTravelCost, linkCandidateCurrent, linkCandidateNext);
	}

}
