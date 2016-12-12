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
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.RouteShape;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.ShapedTransitSchedule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a Router for each shape (given by gtfs). Multiple transit routes
 * might use the same shape.
 *
 * @see RouterShapes
 *
 * @author polettif
 */
public class ScheduleRoutersWithShapes implements ScheduleRouters {

	protected static Logger log = Logger.getLogger(ScheduleRoutersWithShapes.class);

	private final PublicTransitMappingConfigGroup config;
	private final ShapedTransitSchedule shapedSchedule;
	private final boolean useArtificial;
	private Map<TransitLine, Map<TransitRoute, Router>> routers = new HashMap<>();
	private Map<Id<RouteShape>, Router> routersByShape = new HashMap<>();
	private Network network;

	public ScheduleRoutersWithShapes(PublicTransitMappingConfigGroup config, ShapedTransitSchedule shapedSchedule, Network network, boolean useArtificial) {
		this.config = config;
		this.shapedSchedule = shapedSchedule;
		this.network = network;
		this.useArtificial = useArtificial;

		init();
	}

	public ScheduleRoutersWithShapes(PublicTransitMappingConfigGroup config, ShapedTransitSchedule shapedSchedule, Network network) {
		this.config = config;
		this.shapedSchedule = shapedSchedule;
		this.network = network;
		this.useArtificial = false;

		init();
	}


	public void init() {
		RouterShapes.setTravelCostType(config.getTravelCostType());
		RouterShapes.setNetworkCutBuffer(200);
		RouterShapes.setMaxWeightDistance(50);

		for(TransitLine transitLine : this.shapedSchedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				log.info("Initiating network and router for transit route " + transitRoute.getId() + " (line " + transitLine.getId() + ")");
				Id<RouteShape> shapeId = shapedSchedule.getShape(transitLine.getId(), transitRoute.getId()).getId();
				Router tmpRouter = routersByShape.get(shapeId);
				if(tmpRouter == null) {
					log.info("New router for shape \"" + shapeId + "\"");
					Set<String> networkTransportModes = config.getModeRoutingAssignment().get(transitRoute.getTransportMode());
					if(useArtificial) networkTransportModes.add(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE);

					tmpRouter = new RouterShapes(network, networkTransportModes, shapedSchedule.getShape(transitLine.getId(), transitRoute.getId()));
					routersByShape.put(shapeId, tmpRouter);
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
		return routers.get(transitLine).get(transitRoute);
	}

	@Override
	public Router getRouter(String scheduleMode) {
		return null;
	}

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
		return routers.get(transitLine).get(transitRoute).getArtificialLinkLength(maxAllowedTravelCost, linkCandidateCurrent, linkCandidateNext);
	}

}
