package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.FastAStarEuclideanFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.utils.objectattributes.attributeconverters.BooleanConverter;
import org.matsim.vehicles.Vehicle;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a Router for each shape (given by gtfs). Multiple transit routes
 * might use the same shape.
 *
 * @author polettif
 */
public class ScheduleRoutersWithShapes implements ScheduleRouters {

	protected static Logger log = Logger.getLogger(ScheduleRoutersWithShapes.class);

	private final PublicTransitMappingConfigGroup config;
	private final TransitSchedule schedule;
	private final Map<Id<RouteShape>, RouteShape> shapes;
	private final double maxWeightDistance;
	private final double cutBuffer;
	private final LocalRouter genericRouter;

	private Map<TransitLine, Map<TransitRoute, LeastCostPathCalculator>> routers = new HashMap<>();
	private Map<TransitLine, Map<TransitRoute, Boolean>> mapArtificial = new HashMap<>();
	private Map<Id<RouteShape>, LeastCostPathCalculator> routersByShape = new HashMap<>();
	private Network network;
	private Map<TransitLine, Map<TransitRoute, Network>> networks = new HashMap<>();


	public ScheduleRoutersWithShapes(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network, Map<Id<RouteShape>, RouteShape> shapes, double maxWeightDistance, double cutBuffer) {
		this.config = config;
		this.schedule = schedule;
		this.network = network;
		this.shapes = shapes;
		this.maxWeightDistance = maxWeightDistance;
		this.cutBuffer = cutBuffer;
		this.genericRouter = new LocalRouter(null);

	}

	public ScheduleRoutersWithShapes(PublicTransitMappingConfigGroup config, TransitSchedule schedule, Network network, Map<Id<RouteShape>, RouteShape> shapes, double maxWeightDistance) {
		this(config, schedule, network, shapes, maxWeightDistance, 5 * maxWeightDistance);
	}


	@Override
	public void load() {
		Counter c = new Counter(" route # ");

		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				c.incCounter();
				Id<RouteShape> shapeId = ScheduleTools.getShapeId(transitRoute);
				RouteShape shape = shapes.get(shapeId);

				LeastCostPathCalculator tmpRouter = null;
				Network cutNetwork = null;

				if(shape == null) {
					MapUtils.getMap(transitLine, mapArtificial).put(transitRoute, true);
					log.warn("No shape available. Transit Route will be mapped artificially! Consider removing routes without shapes beforehand.");
				}
				else {
					MapUtils.getMap(transitLine, mapArtificial).put(transitRoute, false);
					tmpRouter = routersByShape.get(shapeId);
					if(tmpRouter == null) {
						Set<String> networkTransportModes = config.getModeRoutingAssignment().get(transitRoute.getTransportMode());

						// todo this setup could be improved (i.e. don't recreate/filter networks all over again)
						cutNetwork = NetworkTools.createFilteredNetworkByLinkMode(network, networkTransportModes);
						Collection<Node> nodesWithinBuffer = ShapeTools.getNodesWithinBuffer(cutNetwork, shape, cutBuffer);
						NetworkTools.cutNetwork(cutNetwork, nodesWithinBuffer);

						LocalRouter r = new LocalRouter(shape);

						FastAStarEuclideanFactory factory = new FastAStarEuclideanFactory(cutNetwork, r);
						tmpRouter = factory.createPathCalculator(cutNetwork, r, r);

						routersByShape.put(shapeId, tmpRouter);
					}
				}
				MapUtils.getMap(transitLine, networks).put(transitRoute, cutNetwork);
				MapUtils.getMap(transitLine, routers).put(transitRoute, tmpRouter);
			}
		}
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute) {
		return this.calcLeastCostPath(fromLinkCandidate.getToNodeId(), toLinkCandidate.getFromNodeId(), transitLine, transitRoute);
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId, TransitLine transitLine, TransitRoute transitRoute) {
		Network n = networks.get(transitLine).get(transitRoute);
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



	/**
	 * Class is sent to path calculator factory
	 */
	private class LocalRouter implements TravelDisutility, TravelTime {

		private final RouteShape shape;

		LocalRouter(RouteShape shape) {
			this.shape = shape;
		}

		/**
		 * Calculates the travel cost and change it based on distance to path
		 */
		private double calcLinkTravelCost(Link link) {
			double travelCost = (config.getTravelCostType().equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());

			if(shape != null) {
				double dist = ShapeTools.calcMinDistanceToShape(link, shape);
				double factor = dist / maxWeightDistance + 0.1;
				if(factor > 1) factor = 3;
				travelCost *= factor;
			}
			return travelCost;
		}

		@Override
		public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
			return this.calcLinkTravelCost(link);
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return this.calcLinkTravelCost(link);
		}

		@Override
		public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
			return link.getLength() / link.getFreespeed();
		}
	}

}
