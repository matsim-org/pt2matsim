package org.matsim.pt2matsim.mapping.networkRouter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.FastAStarEuclideanFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.PTMapperTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
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
public class ScheduleRoutersGtfsShapes implements ScheduleRouters {

	protected static Logger log = Logger.getLogger(ScheduleRoutersGtfsShapes.class);

	// standard fields
	private final TransitSchedule schedule;
	private final Network network;
	private final Map<String, Set<String>> transportModeAssignment;
	private final PublicTransitMappingConfigGroup.TravelCostType travelCostType;


	// path calculators
	private final Map<Id<RouteShape>, PathCalculator> pathCalculatorsByShape = new HashMap<>();
	private final Map<Id<RouteShape>, ShapeRouter> shapeRoutersByShape = new HashMap<>();
	private final Map<Id<RouteShape>, Network> networksByShape = new HashMap<>();
	// shape fields
	private final Map<Id<RouteShape>, RouteShape> shapes;
	private final double maxWeightDistance;
	private final double cutBuffer;
	private final Map<TransitLine, Map<TransitRoute, PathCalculator>> pathCalculators = new HashMap<>();
	private final Map<TransitLine, Map<TransitRoute, Boolean>> mapArtificial = new HashMap<>();
	private final Map<TransitLine, Map<TransitRoute, Network>> networks = new HashMap<>();
	private final Map<TransitLine, Map<TransitRoute, ShapeRouter>> shapeRouters = new HashMap<>();


	private ScheduleRoutersGtfsShapes(TransitSchedule schedule, Network network, Map<Id<RouteShape>, RouteShape> shapes, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType travelCostType, double maxWeightDistance, double cutBuffer) {
		this.schedule = schedule;
		this.network = network;
		this.transportModeAssignment = transportModeAssignment;
		this.travelCostType = travelCostType;
		this.shapes = shapes;
		this.maxWeightDistance = maxWeightDistance;
		this.cutBuffer = cutBuffer;

		load();
	}


	/**
	 * Load path calculators for all transit routes
	 */
	private void load() {
		Counter c = new Counter(" route # ");

		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				c.incCounter();
				Id<RouteShape> shapeId = ScheduleTools.getShapeId(transitRoute);
				RouteShape shape = shapes.get(shapeId);

				PathCalculator pathCalculator;
				Network cutNetwork;

				if(shape == null) {
					MapUtils.getMap(transitLine, mapArtificial).put(transitRoute, true);
					log.warn("No shape available. Transit Route will be mapped artificially! Consider removing routes without shapes beforehand.");
				}
				else {
					MapUtils.getMap(transitLine, mapArtificial).put(transitRoute, false);
					pathCalculator = pathCalculatorsByShape.get(shapeId);
					if(pathCalculator == null) {
						Set<String> networkTransportModes = transportModeAssignment.get(transitRoute.getTransportMode());

						// todo this setup could be improved (i.e. don't recreate/filter networks all over again)
						cutNetwork = NetworkTools.createFilteredNetworkByLinkMode(network, networkTransportModes);
						Collection<Node> nodesWithinBuffer = ShapeTools.getNodesWithinBuffer(cutNetwork, shape, cutBuffer);
						NetworkTools.cutNetwork(cutNetwork, nodesWithinBuffer);

						ShapeRouter r = new ShapeRouter(shape);
						pathCalculator = new PathCalculator(new FastAStarEuclideanFactory().createPathCalculator(cutNetwork, r, r));

						pathCalculatorsByShape.put(shapeId, pathCalculator);
						networksByShape.put(shapeId, cutNetwork);
						shapeRoutersByShape.put(shapeId, r);
					}
				}
				MapUtils.getMap(transitLine, networks).put(transitRoute, networksByShape.get(shapeId));
				MapUtils.getMap(transitLine, pathCalculators).put(transitRoute, pathCalculatorsByShape.get(shapeId));
				MapUtils.getMap(transitLine, shapeRouters).put(transitRoute, shapeRoutersByShape.get(shapeId));
			}
		}
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute) {
		return this.calcLeastCostPath(fromLinkCandidate.getLink().getToNode().getId(), toLinkCandidate.getLink().getFromNode().getId(), transitLine, transitRoute);
	}

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId, TransitLine transitLine, TransitRoute transitRoute) {
		Network n = networks.get(transitLine).get(transitRoute);
		if(n == null) return null;

		Node fromNode = n.getNodes().get(fromNodeId);
		Node toNode = n.getNodes().get(toNodeId);
		if(fromNode == null || toNode == null) return null;

		return pathCalculators.get(transitLine).get(transitRoute).calcPath(fromNode, toNode);
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute) {
		return PTMapperTools.calcMinTravelCost(fromTransitRouteStop, toTransitRouteStop, travelCostType);
	}

	@Override
	public double getLinkCandidateTravelCost(LinkCandidate candidate) {
		return shapeRouters.get(candidate.getStop().getTransitLine()).get(candidate.getStop().getTransitRoute()).calcLinkTravelCost(candidate.getLink());
	}

	/**
	 * Class is sent to path calculator factory
	 */
	private class ShapeRouter implements TravelDisutility, TravelTime {

		private final RouteShape shape;

		ShapeRouter(RouteShape shape) {
			this.shape = shape;
		}

		/**
		 * Calculates the travel cost and change it based on distance to path
		 */
		private double calcLinkTravelCost(Link link) {
			double travelCost = PTMapperTools.calcTravelCost(link, travelCostType);

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

	static public class Factory implements ScheduleRoutersFactory {
		final private TransitSchedule schedule;
		final private Network network;
		final private Map<Id<RouteShape>, RouteShape> shapes;
		final private Map<String, Set<String>> transportModeAssignment;
		final private PublicTransitMappingConfigGroup.TravelCostType travelCostType;
		final private double maxWeightDistance;
		final private double cutBuffer;
		
		public Factory(TransitSchedule schedule, Network network, Map<Id<RouteShape>, RouteShape> shapes, Map<String, Set<String>> transportModeAssignment, PublicTransitMappingConfigGroup.TravelCostType travelCostType, double maxWeightDistance, double cutBuffer) {
			this.schedule = schedule;
			this.network = network;
			this.shapes = shapes;
			this.transportModeAssignment = transportModeAssignment;
			this.travelCostType = travelCostType;
			this.maxWeightDistance = maxWeightDistance;
			this.cutBuffer = cutBuffer;
		}

		@Override
		public ScheduleRouters createInstance() {
			return new ScheduleRoutersGtfsShapes(schedule, network, shapes, transportModeAssignment, travelCostType, maxWeightDistance, cutBuffer);
		}
		
	}
}
